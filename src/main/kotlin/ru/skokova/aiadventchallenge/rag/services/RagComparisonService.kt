package ru.skokova.aiadventchallenge.rag.services

import org.slf4j.LoggerFactory
import ru.skokova.aiadventchallenge.rag.client.YandexGptClient
import ru.skokova.aiadventchallenge.rag.models.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RagComparisonService(
    private val searchService: SearchService,
    private val yandexGptClient: YandexGptClient
) {
    private val logger = LoggerFactory.getLogger(RagComparisonService::class.java)

    /**
     * Генерирует ответ С использованием RAG
     */
    suspend fun answerWithRag(
        question: String,
        index: VectorIndex,
        topK: Int = 3
    ): RagAnswer {
        println("\n🔍 Генерация ответа С RAG...")
        logger.info("Генерация ответа С RAG для вопроса: $question")

        // Получаем релевантные чанки
        val searchResults = searchService.search(question, index, topK)
        val chunks = searchResults.map { result ->
            ChunkWithScore(
                text = result.chunk.text,
                sourceFile = result.chunk.metadata.sourceFile,
                score = result.similarity
            )
        }

        // Выводим найденные чанки в консоль
        println("📚 Найденные чанки:")
        chunks.forEachIndexed { i, chunk ->
            println("  ${i + 1}. [${chunk.sourceFile}] (Score: %.4f)".format(chunk.score))
        }

        // Формируем контекст из чанков
        val context = chunks.joinToString("\n\n") { chunk ->
            "[${chunk.sourceFile}]\n${chunk.text}"
        }

        val systemPrompt = """
Ты — эксперт по машинному обучению и RAG системам.
Ответь на вопрос пользователя, используя ТОЛЬКО информацию из предоставленного контекста.
Если в контексте нет информации для ответа, честно скажи об этом.
Обязательно ссылайся на источники в формате [источник: <имя_файла>].
        """.trimIndent()

        val userPrompt = """
Контекст:
$context

Вопрос: $question
        """.trimIndent()

        val answer = yandexGptClient.generateText(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            temperature = 0.3,
            maxTokens = 2000
        )

        // Выводим ответ с RAG
        println("\n✅ Ответ С RAG:")
        println(answer)
        println("\n" + "=".repeat(80))

        return RagAnswer(answer = answer, chunks = chunks)
    }

    /**
     * Генерирует ответ БЕЗ RAG
     */
    suspend fun answerWithoutRag(question: String): String {
        println("\n🤔 Генерация ответа БЕЗ RAG...")
        logger.info("Генерация ответа БЕЗ RAG для вопроса: $question")

        val systemPrompt = """
Ты — эксперт по машинному обучению и RAG системам.
Ответь на вопрос пользователя на основе своих знаний.
        """.trimIndent()

        val answer = yandexGptClient.generateText(
            systemPrompt = systemPrompt,
            userPrompt = question,
            temperature = 0.3,
            maxTokens = 2000
        )

        // Выводим ответ без RAG
        println("\n❌ Ответ БЕЗ RAG:")
        println(answer)
        println("\n" + "=".repeat(80))

        return answer
    }

    /**
     * Выполняет полное сравнение двух подходов
     */
    suspend fun compareApproaches(
        questions: List<String>,
        index: VectorIndex
    ): ComparisonReport {
        logger.info("Начинаю сравнение RAG vs БЕЗ-RAG для ${questions.size} вопросов")

        val comparisons = questions.mapIndexed { idx, question ->
            println("\n" + "=".repeat(80))
            println("📝 Вопрос ${idx + 1}/${questions.size}: \"$question\"")
            println("=".repeat(80))
            
            val ragAnswer = answerWithRag(question, index)
            val noRagAnswer = answerWithoutRag(question)
            
            val analysis = analyzeAnswers(ragAnswer, noRagAnswer)
            
            QuestionComparison(
                question = question,
                answerWithRag = ragAnswer,
                answerWithoutRag = noRagAnswer,
                analysis = analysis
            )
        }

        val summary = generateSummary(comparisons, index)
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))

        return ComparisonReport(
            questions = comparisons,
            summary = summary,
            createdAt = timestamp
        )
    }

    private fun analyzeAnswers(ragAnswer: RagAnswer, noRagAnswer: String): String {
        val hasSourceReferences = ragAnswer.answer.contains("[источник:")
        val avgScore = ragAnswer.chunks.map { it.score }.average()
        
        // Проверяем, есть ли в ответе без RAG признаки отсутствия знаний
        val noRagLacksKnowledge = noRagAnswer.contains("нет информации", ignoreCase = true) ||
                                  noRagAnswer.contains("не знаю", ignoreCase = true) ||
                                  noRagAnswer.contains("к сожалению", ignoreCase = true) ||
                                  noRagAnswer.contains("обратитесь к документации", ignoreCase = true) ||
                                  noRagAnswer.contains("уточните", ignoreCase = true)
        
        // Проверяем, дал RAG конкретный ответ с источниками
        val ragProvidesSpecificAnswer = hasSourceReferences && 
                                         ragAnswer.answer.length > 50 &&
                                         !ragAnswer.answer.contains("нет информации", ignoreCase = true)
        
        // RAG помог, если:
        // 1. Модель без RAG не знает ответа (говорит "нет информации")
        // 2. RAG даёт конкретный ответ с источниками
        // 3. Score чанков не слишком низкий (>0.35)
        val ragHelped = noRagLacksKnowledge && ragProvidesSpecificAnswer && avgScore > 0.35
        
        return buildString {
            appendLine("**Точность фактов:** ${if (avgScore > 0.6) "высокая" else if (avgScore > 0.4) "средняя" else "низкая"}")
            appendLine("**Конкретика:** ${if (hasSourceReferences) "есть ссылки на источники" else "общие фразы"}")
            appendLine("**Средний score чанков:** %.4f".format(avgScore))
            
            if (ragHelped) {
                appendLine("**Вывод:** ✅ RAG помог! Модель без RAG не знала ответа, а с RAG предоставила точную информацию из документов")
            } else if (avgScore > 0.6) {
                appendLine("**Вывод:** RAG предоставил точную информацию из документов (высокий score)")
            } else {
                appendLine("**Вывод:** RAG не дал существенного преимущества")
            }
        }
    }

    private fun generateSummary(comparisons: List<QuestionComparison>, index: VectorIndex): String {
        val ragHelpedCount = comparisons.count { 
            it.analysis.contains("RAG помог") 
        }
        
        // Подсчёт уникальных документов
        val uniqueDocuments = index.chunks
            .map { it.metadata.sourceFile }
            .distinct()
            .sorted()
        
        return buildString {
            appendLine("## 📈 Общие выводы")
            appendLine()
            appendLine("### Использованный индекс:")
            appendLine("- **Документов:** ${uniqueDocuments.size}")
            uniqueDocuments.forEach { doc ->
                appendLine("  - $doc")
            }
            appendLine("- **Чанков:** ${index.chunks.size}")
            appendLine()
            appendLine("### Статистика:")
            appendLine("- Всего вопросов: ${comparisons.size}")
            appendLine("- ✅ RAG помог: $ragHelpedCount/${comparisons.size}")
            appendLine("- ❌ RAG не помог: ${comparisons.size - ragHelpedCount}/${comparisons.size}")
            appendLine()
            
            if (ragHelpedCount > 0) {
                appendLine("### ✅ Где RAG помог:")
                appendLine("- Модель без RAG не знала ответа (\"нет информации\")")
                appendLine("- RAG предоставил точные факты с ссылками на источники")
                appendLine("- Вопросы про специфичные детали проектов")
            } else {
                appendLine("### ⚠️ Почему RAG не помог:")
                appendLine("- Модель уже знала ответы на вопросы")
                appendLine("- Низкое косинусное сходство чанков")
                appendLine("- Вопросы слишком общие или не по теме документов")
            }
            
            appendLine()
            appendLine("### 💡 Рекомендации:")
            appendLine("- Использовать RAG для специфичных вопросов про ваши проекты")
            appendLine("- Добавлять документы с уникальной информацией")
            appendLine("- Экспериментировать с размером чанков и overlap")
        }
    }
}
