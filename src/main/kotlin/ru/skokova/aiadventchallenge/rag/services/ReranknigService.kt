package ru.skokova.aiadventchallenge.rag.services

import org.slf4j.LoggerFactory
import ru.skokova.aiadventchallenge.rag.client.YandexGptClient
import ru.skokova.aiadventchallenge.rag.models.RerankResult
import ru.skokova.aiadventchallenge.rag.models.RerankingMetrics
import kotlinx.coroutines.delay

class RerankingService(private val gptClient: YandexGptClient) {
    private val logger = LoggerFactory.getLogger(RerankingService::class.java)

    fun filterByThreshold(
        results: List<SearchResult>,
        threshold: Double = 0.35
    ): List<RerankResult> {
        logger.info("Применяю threshold фильтрацию с порогом $threshold")

        return results.map { result ->
            RerankResult(
                chunk = result.chunk,
                originalSimilarity = result.similarity,
                rerankScore = null,
                isRelevant = result.similarity >= threshold
            )
        }
    }

    suspend fun rerankWithLLM(
        query: String,
        results: List<SearchResult>,
        threshold: Double = 0.35
    ): List<RerankResult> {
        logger.info("Применяю LLM реранкинг для ${results.size} чанков")

        val reranked = mutableListOf<RerankResult>()

        // Строгий системный промпт
        val systemPrompt = "Ты - алгоритм оценки релевантности. Твоя задача - выдать числовое значение сходства."

        for ((index, result) in results.withIndex()) {
            // Промпт без лишних слов, чтобы парсинг не ломался
            val userPrompt = """
                Оцени по шкале от 0.0 до 1.0, насколько фрагмент полезен для ответа на вопрос.
                
                ВОПРОС: "$query"
                
                ФРАГМЕНТ: 
                "${result.chunk.text.take(1000)}"
                
                КРИТЕРИИ:
                1.0 = Содержит прямой и полный ответ.
                0.5 = Содержит частичную информацию или контекст.
                0.1 = Тема та же, но ответ бесполезен.
                0.0 = Совершенно нерелевантно.
                
                ВЫВОД: Только одно число (например, 0.8). Никаких объяснений.
            """.trimIndent()

            try {
                val llmScoreText = gptClient.generateText(
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt
                )

                // Более надежный парсинг: ищем первое число с плавающей точкой
                // Regex ищет что-то похожее на 0.5, 1.0, 0,8
                val scoreRegex = Regex("""(\d+[.,]?\d*)""")
                val match = scoreRegex.find(llmScoreText)

                val llmScore = match?.value
                    ?.replace(",", ".")
                    ?.toDoubleOrNull()
                    ?: 0.0 // Если парсинг не удался, ставим 0

                // Формула весов (оставляем агрессивную)
                val combinedScore = (result.similarity * 0.3) + (llmScore * 0.7)

                reranked.add(
                    RerankResult(
                        chunk = result.chunk,
                        originalSimilarity = result.similarity,
                        rerankScore = combinedScore,
                        isRelevant = combinedScore >= threshold
                    )
                )

                delay(300) // Чуть уменьшим задержку

            } catch (e: Exception) {
                logger.error("Ошибка LLM: ${e.message}")
                // Fallback
                reranked.add(
                    RerankResult(
                        chunk = result.chunk,
                        originalSimilarity = result.similarity,
                        rerankScore = result.similarity,
                        isRelevant = result.similarity >= threshold
                    )
                )
            }
        }

        return reranked.sortedByDescending { it.rerankScore ?: it.originalSimilarity }
    }

    fun calculateMetrics(results: List<RerankResult>): RerankingMetrics {
        val relevantResults = results.filter { it.isRelevant }
        val similarities = results.map { it.rerankScore ?: it.originalSimilarity }

        return RerankingMetrics(
            totalChunks = results.size,
            relevantChunks = relevantResults.size,
            averageSimilarity = if (similarities.isNotEmpty())
                similarities.average() else 0.0,
            minSimilarity = similarities.minOrNull() ?: 0.0,
            maxSimilarity = similarities.maxOrNull() ?: 0.0,
            filteredOutCount = results.count { !it.isRelevant }
        )
    }
}
