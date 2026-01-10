package ru.skokova.aiadventchallenge.rag.models

import kotlinx.serialization.Serializable

enum class MessageRole {
    SYSTEM, USER, ASSISTANT
}

@Serializable
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: String = java.time.LocalDateTime.now().toString()
)

@Serializable
data class ChatHistory(
    val messages: MutableList<ChatMessage> = mutableListOf()
)
