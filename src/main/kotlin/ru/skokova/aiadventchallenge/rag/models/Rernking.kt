package ru.skokova.aiadventchallenge.rag.models

import kotlinx.serialization.Serializable
import ru.skokova.aiadventchallenge.rag.services.SearchResult

@Serializable
data class RerankResult(
    val chunk: DocumentChunk,
    val originalSimilarity: Double,
    val rerankScore: Double? = null, // null для threshold-only метода
    val isRelevant: Boolean
)

@Serializable
data class RerankingComparison(
    val question: String,
    val resultsNoFilter: List<SearchResult>,
    val resultsThresholdFilter: List<RerankResult>,
    val resultsLlmRerank: List<RerankResult>,
    val answerNoFilter: String,
    val answerWithThreshold: String,
    val answerWithLlmRerank: String,
    val thresholdUsed: Double,
    val metricsNoFilter: RerankingMetrics,
    val metricsThreshold: RerankingMetrics,
    val metricsLlmRerank: RerankingMetrics
)

@Serializable
data class RerankingMetrics(
    val totalChunks: Int,
    val relevantChunks: Int,
    val averageSimilarity: Double,
    val minSimilarity: Double,
    val maxSimilarity: Double,
    val filteredOutCount: Int
)
