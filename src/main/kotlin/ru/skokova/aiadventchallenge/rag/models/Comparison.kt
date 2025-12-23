package ru.skokova.aiadventchallenge.rag.models

import kotlinx.serialization.Serializable

@Serializable
data class RagAnswer(
    val answer: String,
    val chunks: List<ChunkWithScore>
)

@Serializable
data class ChunkWithScore(
    val text: String,
    val sourceFile: String,
    val score: Double
)

@Serializable
data class ComparisonReport(
    val questions: List<QuestionComparison>,
    val summary: String,
    val createdAt: String
)

@Serializable
data class QuestionComparison(
    val question: String,
    val answerWithRag: RagAnswer,
    val answerWithoutRag: String,
    val analysis: String
)
