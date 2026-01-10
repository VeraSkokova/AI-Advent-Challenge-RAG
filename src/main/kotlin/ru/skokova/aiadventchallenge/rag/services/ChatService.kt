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
    private val maxHistoryMessages = 10
    private val topK = 3

    suspend fun processQuery(query: String, index: VectorIndex): ChatResponse {
        logger.info("Обработка запроса: $query")

        // 1. Поиск релевантных документов (Retrieval)
        val searchResults = searchService.search(query, index, topK)
        val relevantChunks = searchResults.filter { it.similarity >= similarityThreshold }

        // 2. Формирование источников для вывода
        val sources = relevantChunks.map { result ->
            SourceInfo(
                fileName = result.chunk.metadata.sourceFile,
                relevance = result.similarity,
                snippet = result.chunk.text.take(100).replace("\n", " ")
            )
        }

        // 3. Формирование контекста из документов
        val contextText = if (relevantChunks.isNotEmpty()) {
            relevantChunks.joinToString("\n\n") { result ->
                "[Источник: ${result.chunk.metadata.sourceFile}]\n${result.chunk.text}"
            }
        } else {
            ""
        }

        // 4. Управление размером истории (сжатие при необходимости)
        manageHistorySize()

        // 5. Формирование системного промпта с контекстом и историей
        val systemPrompt = buildSystemPrompt(contextText, relevantChunks.isNotEmpty())

        // 6. Формирование полного промпта с историей
        val fullPrompt = buildFullPrompt(systemPrompt, query)

        // 7. Генерация ответа (Generation)
        val answer = try {
            yandexGptClient.generateText(
                systemPrompt = systemPrompt,
                userPrompt = buildUserPromptWithHistory(query),
                temperature = 0.3,
                maxTokens = 2000
            )
        } catch (e: Exception) {
            logger.error("Ошибка генерации ответа: ${e.message}", e)
            "❌ Ошибка генерации ответа: ${e.message}"
        }

        // 8. Обновление истории
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
            Если информации в контексте недостаточно для полного ответа, используй общие знания, но обязательно упомяни это.
            Всегда ссылайся на источники в формате [источник: имя_файла], когда используешь информацию из контекста.
            
            === КОНТЕКСТ ИЗ БАЗЫ ЗНАНИЙ ===
            $context
            """.trimIndent()
        } else {
            """
            Ты — умный ассистент.
            В базе знаний не найдено релевантной информации для текущего вопроса.
            Ответь на основе своих общих знаний, но будь честен, если не уверен.
            """.trimIndent()
        }
    }

    private fun buildUserPromptWithHistory(currentQuery: String): String {
        val historyText = if (chatHistory.messages.isNotEmpty()) {
            val recentMessages = chatHistory.messages.takeLast(6) // Последние 3 пары
            val formattedHistory = recentMessages.joinToString("\n") { msg ->
                when (msg.role) {
                    MessageRole.USER -> "Пользователь: ${msg.content}"
                    MessageRole.ASSISTANT -> "Ассистент: ${msg.content}"
                    MessageRole.SYSTEM -> "Система: ${msg.content}"
                }
            }
            "\n\n=== ИСТОРИЯ ДИАЛОГА ===\n$formattedHistory\n"
        } else {
            ""
        }

        return """
            $historyText
            
            === ТЕКУЩИЙ ВОПРОС ===
            $currentQuery
        """.trimIndent()
    }

    private fun buildFullPrompt(systemPrompt: String, query: String): String {
        return "$systemPrompt\n\n${buildUserPromptWithHistory(query)}"
    }

    private suspend fun manageHistorySize() {
        if (chatHistory.messages.size > maxHistoryMessages) {
            // Простое сжатие: оставляем последние N сообщений
            val recentMessages = chatHistory.messages.takeLast(maxHistoryMessages).toMutableList()

            // Опционально: можно добавить суммаризацию старых сообщений
            // val oldMessages = chatHistory.messages.dropLast(maxHistoryMessages)
            // val summary = summarizeOldMessages(oldMessages)
            // recentMessages.add(0, ChatMessage(MessageRole.SYSTEM, "Краткое содержание предыдущего диалога: $summary"))

            chatHistory.messages.clear()
            chatHistory.messages.addAll(recentMessages)
            historyRepository.save(chatHistory)

            logger.info("История сжата: оставлено последних $maxHistoryMessages сообщений")
        }
    }

    fun clearHistory() {
        chatHistory.messages.clear()
        historyRepository.save(chatHistory)
        logger.info("История диалога очищена")
    }

    fun getHistorySize(): Int = chatHistory.messages.size
}
