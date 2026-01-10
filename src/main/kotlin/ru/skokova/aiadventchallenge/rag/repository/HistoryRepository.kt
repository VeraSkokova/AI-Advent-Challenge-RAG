package ru.skokova.aiadventchallenge.rag.repository

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.skokova.aiadventchallenge.rag.models.ChatHistory
import java.io.File

class HistoryRepository(
    private val historyFilePath: String = "chat_history.json"
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val historyFile = File(historyFilePath)

    fun load(): ChatHistory {
        if (!historyFile.exists()) {
            return ChatHistory()
        }

        return try {
            val content = historyFile.readText()
            json.decodeFromString<ChatHistory>(content)
        } catch (e: Exception) {
            println("⚠️  Ошибка загрузки истории: ${e.message}. Создана новая история.")
            ChatHistory()
        }
    }

    fun save(history: ChatHistory) {
        try {
            historyFile.writeText(json.encodeToString(history))
        } catch (e: Exception) {
            println("❌ Ошибка сохранения истории: ${e.message}")
        }
    }

    fun clear() {
        if (historyFile.exists()) {
            historyFile.delete()
        }
    }
}
