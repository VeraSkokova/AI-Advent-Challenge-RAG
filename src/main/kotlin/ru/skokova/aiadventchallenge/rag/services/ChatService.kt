package ru.skokova.aiadventchallenge.rag.services

import org.slf4j.LoggerFactory
import ru.skokova.aiadventchallenge.rag.client.YandexGptClient
import ru.skokova.aiadventchallenge.rag.models.*
import ru.skokova.aiadventchallenge.rag.repository.HistoryRepository

data class ChatResponse(
    val answer: String,
    val sources: List<SourceInfo>
)

data class SourceInfo(
    val fileName: String,
    val relevance: Double,
    val snippet: String
)

class ChatService(
    private val searchService: SearchService,
    private val yandexGptClient: YandexGptClient,
    private val historyRepository: HistoryRepository
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    private var chatHistory: ChatHistory = historyRepository.load()

    private val similarityThreshold = 0.35
    private val maxHistoryMessages = 6 // Держим только 6 последних сообщений (3 пары), остальное в саммари
    private val topK = 3

    suspend fun processQuery(query: String, index: VectorIndex): ChatResponse {
        // 1. Поиск релевантных документов (Retrieval)
        val searchResults = searchService.search(query, index, topK)
        val relevantChunks = searchResults.filter { it.similarity >= similarityThreshold }

        // 2. Формирование источников
        val sources = relevantChunks.map { result ->
            SourceInfo(
                fileName = result.chunk.metadata.sourceFile,
                relevance = result.similarity,
                snippet = result.chunk.text.take(100).replace("\n", " ")
            )
        }

        // 3. Контекст из документов
        val contextText = if (relevantChunks.isNotEmpty()) {
            relevantChunks.joinToString("\n\n") { result ->
                "[Источник: ${result.chunk.metadata.sourceFile}]\n${result.chunk.text}"
            }
        } else {
            ""
        }

        // 4. Сжатие истории, если нужно
        manageHistorySize()

        // 5. Генерация ответа
        val systemPrompt = buildSystemPrompt(contextText, relevantChunks.isNotEmpty())
        val userPrompt = buildUserPromptWithHistory(query)

        val answer = try {
            yandexGptClient.generateText(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                temperature = 0.3,
                maxTokens = 2000
            )
        } catch (e: Exception) {
            "❌ Ошибка генерации ответа: ${e.message}"
        }

        // 6. Обновление истории
        chatHistory.messages.add(ChatMessage(MessageRole.USER, query))
        chatHistory.messages.add(ChatMessage(MessageRole.ASSISTANT, answer))
        historyRepository.save(chatHistory)

        return ChatResponse(answer = answer, sources = sources)
    }

    private fun buildSystemPrompt(context: String, hasContext: Boolean): String {
        return if (hasContext) {
            """
            Ты — умный ассистент с доступом к базе знаний.
            Твоя задача — отвечать на вопросы пользователя, используя предоставленный контекст из документов.
            Если информации в контексте недостаточно, используй общие знания, но предупреди об этом.
            Всегда ссылайся на источники в формате [источник: имя_файла].
            
            === КОНТЕКСТ ИЗ БАЗЫ ЗНАНИЙ ===
            $context
            """.trimIndent()
        } else {
            """
            Ты — умный ассистент.
            В базе знаний не найдено релевантной информации.
            Ответь на основе своих общих знаний, но будь честен.
            """.trimIndent()
        }
    }

    private fun buildUserPromptWithHistory(currentQuery: String): String {
        val sb = StringBuilder()

        // 1. Добавляем саммари (долгосрочная память)
        if (chatHistory.summary.isNotBlank()) {
            sb.append("=== КРАТКОЕ СОДЕРЖАНИЕ ПРЕДЫДУЩЕГО ДИАЛОГА ===\n")
            sb.append(chatHistory.summary)
            sb.append("\n\n")
        }

        // 2. Добавляем последние сообщения (краткосрочная память)
        if (chatHistory.messages.isNotEmpty()) {
            sb.append("=== ПОСЛЕДНИЕ СООБЩЕНИЯ ===\n")
            chatHistory.messages.forEach { msg ->
                val roleName = when (msg.role) {
                    MessageRole.USER -> "Пользователь"
                    MessageRole.ASSISTANT -> "Ассистент"
                    MessageRole.SYSTEM -> "Система"
                }
                sb.append("$roleName: ${msg.content}\n")
            }
            sb.append("\n")
        }

        // 3. Текущий вопрос
        sb.append("=== ТЕКУЩИЙ ВОПРОС ===\n")
        sb.append(currentQuery)

        return sb.toString()
    }

    private suspend fun manageHistorySize() {
        if (chatHistory.messages.size > maxHistoryMessages) {
            logger.info("История превысила лимит (${chatHistory.messages.size} > $maxHistoryMessages). Запуск суммаризации...")

            // Берем старые сообщения, которые нужно сжать (все, кроме последних N/2, чтобы оставить немного свежего контекста)
            val keepCount = 2 // Оставляем только последнюю пару вопрос-ответ
            val messagesToSummarize = chatHistory.messages.dropLast(keepCount)
            val messagesToKeep = chatHistory.messages.takeLast(keepCount)

            // Формируем текст для суммаризации
            val textToSummarize = messagesToSummarize.joinToString("\n") { "${it.role}: ${it.content}" }

            // Если уже было саммари, добавляем его тоже, чтобы обновить
            val fullContext = if (chatHistory.summary.isNotBlank()) {
                "Предыдущее саммари:\n${chatHistory.summary}\n\nНовые сообщения:\n$textToSummarize"
            } else {
                textToSummarize
            }

            // Запрашиваем суммаризацию у LLM
            val newSummary = summarizeText(fullContext)

            // Обновляем состояние
            chatHistory.summary = newSummary
            chatHistory.messages.clear()
            chatHistory.messages.addAll(messagesToKeep)

            historyRepository.save(chatHistory)
            logger.info("Суммаризация завершена. Новое саммари: \"${newSummary.take(50)}...\"")
        }
    }

    private suspend fun summarizeText(text: String): String {
        val systemPrompt = """
            Ты — эксперт-аналитик. Твоя задача — сжать историю диалога.
            Создай краткое, но информативное саммари (сводку) на русском языке.
            Сохрани ключевые факты, вопросы пользователя и данные, которые он предоставил.
            Результат должен позволить боту продолжить разговор, помня контекст.
        """.trimIndent()

        return try {
            yandexGptClient.generateText(
                systemPrompt = systemPrompt,
                userPrompt = text,
                temperature = 0.3,
                maxTokens = 1000
            )
        } catch (e: Exception) {
            logger.error("Ошибка при суммаризации", e)
            chatHistory.summary // Возвращаем старое, если ошибка
        }
    }

    fun clearHistory() {
        chatHistory.summary = ""
        chatHistory.messages.clear()
        historyRepository.save(chatHistory)
        logger.info("История диалога очищена")
    }

    fun getHistorySize(): Int = chatHistory.messages.size

    // Геттеры для отображения в UI
    fun getSummary(): String = chatHistory.summary
    fun getRecentMessages(): List<ChatMessage> = chatHistory.messages
}
