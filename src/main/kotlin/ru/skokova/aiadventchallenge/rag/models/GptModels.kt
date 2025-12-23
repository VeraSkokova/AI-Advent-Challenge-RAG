package ru.skokova.aiadventchallenge.rag.models

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: String,
    val text: String
)

@Serializable
data class CompletionOptions(
    val stream: Boolean = false,
    val temperature: Double = 0.3,
    val maxTokens: Int = 2000
)

@Serializable
data class GptRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions,
    val messages: List<Message>
)

@Serializable
data class GptResponse(
    val result: ResultData? = null,
    val alternatives: List<Alternative>? = null,
    val error: ErrorData? = null
)

@Serializable
data class ResultData(
    val alternatives: List<Alternative>? = null,
    val usage: UsageData? = null
)

@Serializable
data class Alternative(
    val message: MessageResponse? = null,
    val status: String? = null
)

@Serializable
data class MessageResponse(
    val role: String = "assistant",
    val text: String = ""
)

@Serializable
data class UsageData(
    val totalTokens: Int = 0
)

@Serializable
data class ErrorData(
    val code: Int? = null,
    val message: String? = null
)
