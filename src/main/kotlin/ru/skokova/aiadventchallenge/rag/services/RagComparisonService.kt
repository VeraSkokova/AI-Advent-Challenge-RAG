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
        
        return buildString {
            appendLine("**Точность фактов:** ${if (avgScore > 0.7) "высокая" else if (avgScore > 0.5) "средняя" else "низкая"}")
            appendLine("**Конкретика:** ${if (hasSourceReferences) "есть ссылки на источники" else "общие фразы"}")
            appendLine("**Средний score чанков:** %.4f".format(avgScore))
            appendLine("**Вывод:** ${if (avgScore > 0.6) "RAG помог предоставить точную информацию из документов" else "RAG не дал существенного преимущества"}")
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
            appendLine("- RAG помог: $ragHelpedCount/${comparisons.size}")
            appendLine("- RAG не помог: ${comparisons.size - ragHelpedCount}/${comparisons.size}")
            appendLine()
            appendLine("### Где RAG помог:")
            appendLine("- В вопросах, требующих точных фактов из документации")
            appendLine("- При необходимости ссылок на источники")
            appendLine("- Для специфичных технических деталей")
            appendLine()
            appendLine("### Рекомендации:")
            appendLine("- Использовать RAG для фактологических вопросов")
            appendLine("- Увеличить порог косинусного сходства для более точных результатов")
            appendLine("- Настроить размер чанков под специфику документов")
        }
    }
}
