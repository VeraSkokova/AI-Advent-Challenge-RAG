package ru.skokova.aiadventchallenge.rag.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.skokova.aiadventchallenge.rag.config.Config
import ru.skokova.aiadventchallenge.rag.models.*

class YandexGptClient {
    private val logger = LoggerFactory.getLogger(YandexGptClient::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    private val modelUri = "gpt://${Config.folderId}/yandexgpt/latest"
    private val apiUrl = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"

    suspend fun generateText(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.3,
        maxTokens: Int = 2000
    ): String {
        return generateTextWithRetry(systemPrompt, userPrompt, temperature, maxTokens)
    }

    private suspend fun generateTextWithRetry(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int
    ): String {
        val maxRetries = 3
        var currentRetry = 0

        while (currentRetry < maxRetries) {
            try {
                val request = GptRequest(
                    modelUri = modelUri,
                    completionOptions = CompletionOptions(
                        temperature = temperature,
                        maxTokens = maxTokens
                    ),
                    messages = listOf(
                        Message(role = "system", text = systemPrompt),
                        Message(role = "user", text = userPrompt)
                    )
                )

                val response: GptResponse = client.post(apiUrl) {
                    header("Authorization", "Api-Key ${Config.apiKey}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()

                return response.result?.alternatives?.firstOrNull()?.message?.text
                    ?: response.alternatives?.firstOrNull()?.message?.text
                    ?: throw Exception("Empty response from API")

            } catch (e: Exception) {
                currentRetry++
                logger.warn("Ошибка API YandexGPT (попытка $currentRetry/$maxRetries): ${e.message}")
                if (currentRetry == maxRetries) throw e
                delay(1000L * (1 shl (currentRetry - 1)))
            }
        }
        throw Exception("Не удалось получить ответ от YandexGPT")
    }
}
