package ru.skokova.aiadventchallenge.rag.services

import org.slf4j.LoggerFactory
import ru.skokova.aiadventchallenge.rag.client.YandexGptClient
import ru.skokova.aiadventchallenge.rag.models.*
import java.io.File
import java.time.LocalDateTime

class RerankingComparisonService(
    private val searchService: SearchService,
    private val rerankingService: RerankingService,
    private val gptClient: YandexGptClient
) {
    private val logger = LoggerFactory.getLogger(RerankingComparisonService::class.java)

    suspend fun runComparison(
        questions: List<String>,
        index: VectorIndex,
        threshold: Double = 0.35,
        topK: Int = 5
    ): List<RerankingComparison> {
        logger.info("Запуск сравнения реранкинга для ${questions.size} вопросов")

        val comparisons = mutableListOf<RerankingComparison>()

        for ((idx, question) in questions.withIndex()) {
            logger.info("Вопрос ${idx + 1}/${questions.size}: $question")

            // 1. Поиск без фильтрации
            val resultsNoFilter = searchService.search(question, index, topK)

            // 2. Threshold фильтрация
            val resultsThreshold = rerankingService.filterByThreshold(resultsNoFilter, threshold)

            // 3. LLM реранкинг
            val resultsLlmRerank = rerankingService.rerankWithLLM(question, resultsNoFilter, threshold)

            // Генерация ответов
            val answerNoFilter = generateAnswer(question, resultsNoFilter.map { it.chunk })

            val answerThreshold = generateAnswer(
                question,
                resultsThreshold.filter { it.isRelevant }.map { it.chunk }
            )

            val answerLlmRerank = generateAnswer(
                question,
                resultsLlmRerank.filter { it.isRelevant }.map { it.chunk }
            )

            // Расчёт метрик
            val metricsNoFilter = calculateBasicMetrics(resultsNoFilter)
            val metricsThreshold = rerankingService.calculateMetrics(resultsThreshold)
            val metricsLlmRerank = rerankingService.calculateMetrics(resultsLlmRerank)

            comparisons.add(
                RerankingComparison(
                    question = question,
                    resultsNoFilter = resultsNoFilter,
                    resultsThresholdFilter = resultsThreshold,
                    resultsLlmRerank = resultsLlmRerank,
                    answerNoFilter = answerNoFilter,
                    answerWithThreshold = answerThreshold,
                    answerWithLlmRerank = answerLlmRerank,
                    thresholdUsed = threshold,
                    metricsNoFilter = metricsNoFilter,
                    metricsThreshold = metricsThreshold,
                    metricsLlmRerank = metricsLlmRerank
                )
            )
        }

        return comparisons
    }

    private suspend fun generateAnswer(question: String, chunks: List<DocumentChunk>): String {
        if (chunks.isEmpty()) {
            return "Релевантных документов не найдено после фильтрации."
        }

        val context = chunks.joinToString("\n\n") { chunk ->
            "[${chunk.metadata.sourceFile}]\n${chunk.text}"
        }

        // ИСПРАВЛЕНИЕ: Явное разделение на system и user prompt
        val systemPrompt = "Ты - вопросно-ответная система. Отвечай на вопрос, используя только предоставленный контекст."
        val userPrompt = """
            КОНТЕКСТ:
            $context
            
            ВОПРОС:
            $question
        """.trimIndent()

        return try {
            // Передаем оба параметра явно по именам, чтобы избежать ошибок сигнатуры
            gptClient.generateText(systemPrompt = systemPrompt, userPrompt = userPrompt)
        } catch (e: Exception) {
            logger.error("Ошибка генерации ответа: ${e.message}")
            "Ошибка при генерации ответа: ${e.message}"
        }
    }

    private fun calculateBasicMetrics(results: List<SearchResult>): RerankingMetrics {
        val similarities = results.map { it.similarity }
        return RerankingMetrics(
            totalChunks = results.size,
            relevantChunks = results.size,
            averageSimilarity = if (similarities.isNotEmpty()) similarities.average() else 0.0,
            minSimilarity = similarities.minOrNull() ?: 0.0,
            maxSimilarity = similarities.maxOrNull() ?: 0.0,
            filteredOutCount = 0
        )
    }

    fun generateMarkdownReport(comparisons: List<RerankingComparison>): String {
        val sb = StringBuilder()

        // Безопасная строка для тройных кавычек в Markdown
        val codeBlockFence = "`" + "`" + "`"

        sb.appendLine("# 🔬 Отчёт: Сравнение методов реранкинга (Day 18)")
        sb.appendLine()
        sb.appendLine("**Дата:** ${LocalDateTime.now()}")
        sb.appendLine("**Порог:** ${comparisons.firstOrNull()?.thresholdUsed ?: 0.35}")
        sb.appendLine("**Вопросов:** ${comparisons.size}")
        sb.appendLine()

        // Статистика
        sb.appendLine("## 📊 Общая статистика")
        sb.appendLine()
        sb.appendLine("| Метод | Avg Relevant % | Avg Score | Avg Filtered |")
        sb.appendLine("|-------|----------------|-----------|--------------|")

        val avgNoFilter = comparisons.map { it.metricsNoFilter }.average()
        val avgThreshold = comparisons.map { it.metricsThreshold }.average()
        val avgLlm = comparisons.map { it.metricsLlmRerank }.average()

        sb.appendLine(String.format("| Без фильтра | 100%% | %.3f | 0 |", avgNoFilter.averageSimilarity))

        sb.appendLine(String.format("| Threshold | %.1f%% | %.3f | %.1f |",
            if (avgThreshold.totalChunks > 0) avgThreshold.relevantChunks.toDouble() / avgThreshold.totalChunks * 100 else 0.0,
            avgThreshold.averageSimilarity,
            avgThreshold.filteredOutCount.toDouble()
        ))

        sb.appendLine(String.format("| LLM Rerank | %.1f%% | %.3f | %.1f |",
            if (avgLlm.totalChunks > 0) avgLlm.relevantChunks.toDouble() / avgLlm.totalChunks * 100 else 0.0,
            avgLlm.averageSimilarity,
            avgLlm.filteredOutCount.toDouble()
        ))
        sb.appendLine()

        // Детальный отчет
        comparisons.forEachIndexed { idx, comparison ->
            sb.appendLine("---")
            sb.appendLine("## Вопрос ${idx + 1}: ${comparison.question}")
            sb.appendLine()

            // Таблица топ-5 чанков с Дельтой
            sb.appendLine("### 🧠 Анализ реранкинга (Top-5)")
            sb.appendLine("| Rank | Файл | Orig Score | LLM Score | Delta | Статус |")
            sb.appendLine("|------|------|------------|-----------|-------|--------|")

            // Берем результаты LLM реранкинга (включая нерелевантные, чтобы видеть, что отсеялось)
            comparison.resultsLlmRerank.take(5).forEachIndexed { rank, res ->
                val llmScore = res.rerankScore ?: res.originalSimilarity
                val delta = llmScore - res.originalSimilarity

                // Визуальные маркеры
                val statusIcon = when {
                    !res.isRelevant -> "❌ (Filtered)"
                    delta > 0.1 -> "🚀 (Boosted)"
                    delta < -0.1 -> "📉 (Demoted)"
                    else -> "➖ (Same)"
                }

                val fileName = res.chunk.metadata.sourceFile.take(20) // Обрезаем длинные имена

                sb.appendLine(String.format("| %d | %s | %.3f | **%.3f** | %+.3f | %s |",
                    rank + 1, fileName, res.originalSimilarity, llmScore, delta, statusIcon))
            }
            sb.appendLine()

            // Ответы
            sb.appendLine("### 🤖 Сравнение ответов")

            // Ответ Threshold (как представителя "глупого" фильтра)
            sb.appendLine("**Threshold Filter:**")
            sb.appendLine(codeBlockFence)
            sb.appendLine(comparison.answerWithThreshold.replace(codeBlockFence, "'''").take(300).replace("\n", " "))
            if (comparison.answerWithThreshold.length > 300) sb.append("...")
            sb.appendLine()
            sb.appendLine(codeBlockFence)

            // Ответ LLM Rerank (наш чемпион)
            sb.appendLine("**LLM Rerank:**")
            sb.appendLine(codeBlockFence)
            sb.appendLine(comparison.answerWithLlmRerank.replace(codeBlockFence, "'''").take(300).replace("\n", " "))
            if (comparison.answerWithLlmRerank.length > 300) sb.append("...")
            sb.appendLine()
            sb.appendLine(codeBlockFence)

            sb.appendLine()
        }

        return sb.toString()
    }

    fun saveReport(comparisons: List<RerankingComparison>, filename: String = "RERANKING_REPORT.md") {
        val report = generateMarkdownReport(comparisons)
        File(filename).writeText(report, Charsets.UTF_8)
        logger.info("Отчёт сохранён в $filename")
    }

    private fun List<RerankingMetrics>.average(): RerankingMetrics {
        if (this.isEmpty()) return RerankingMetrics(0, 0, 0.0, 0.0, 0.0, 0)
        return RerankingMetrics(
            totalChunks = this.map { it.totalChunks }.average().toInt(),
            relevantChunks = this.map { it.relevantChunks }.average().toInt(),
            averageSimilarity = this.map { it.averageSimilarity }.average(),
            minSimilarity = this.map { it.minSimilarity }.average(),
            maxSimilarity = this.map { it.maxSimilarity }.average(),
            filteredOutCount = this.map { it.filteredOutCount }.average().toInt()
        )
    }
}
