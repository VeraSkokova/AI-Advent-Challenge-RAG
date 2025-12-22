package ru.skokova.aiadventchallenge.rag

import kotlinx.coroutines.runBlocking
import ru.skokova.aiadventchallenge.rag.client.YandexEmbeddingClient
import ru.skokova.aiadventchallenge.rag.config.Config
import ru.skokova.aiadventchallenge.rag.services.IndexService
import ru.skokova.aiadventchallenge.rag.services.SearchService
import ru.skokova.aiadventchallenge.rag.services.TextChunker
import java.io.File
import java.util.Scanner

fun main() = runBlocking {
    printBanner()

    // Инициализация компонентов
    val client = YandexEmbeddingClient()
    val chunker = TextChunker()
    val indexService = IndexService(client, chunker)
    val searchService = SearchService(client)

    // Загрузка существующего индекса в память при старте
    var currentIndex = indexService.loadIndex()
    if (currentIndex != null) {
        println("✅ Загружен индекс: ${currentIndex.chunks.size} чанков")
    } else {
        println("⚠️ Индекс не найден. Используйте команду 'index' для создания.")
    }

    val scanner = Scanner(System.`in`)
    var isRunning = true

    while (isRunning) {
        print("\n> ")
        val input = scanner.nextLine().trim()
        val parts = input.split(" ", limit = 2)
        val command = parts[0]
        val args = if (parts.size > 1) parts[1] else ""

        when (command) {
            "index" -> {
                if (args.isBlank()) {
                    println("❌ Укажите путь к папке. Пример: index documents/")
                } else {
                    println("🚀 Начинаю индексацию папки: $args")
                    try {
                        currentIndex = indexService.createIndex(args)
                        indexService.saveIndex(currentIndex!!)
                        println("✅ Индексация завершена успешно!")
                    } catch (e: Exception) {
                        println("❌ Ошибка индексации: ${e.message}")
                    }
                }
            }
            "search" -> {
                if (currentIndex == null) {
                    println("⚠️ Сначала создайте индекс!")
                } else if (args.isBlank()) {
                    println("❌ Введите поисковый запрос. Пример: search что такое RAG")
                } else {
                    println("🔍 Ищу: \"$args\"...")
                    val results = searchService.search(args, currentIndex!!)

                    if (results.isEmpty()) {
                        println("Ничего не найдено 😔")
                    } else {
                        results.forEachIndexed { i, res ->
                            println("\n--- Результат #${i + 1} (Score: %.4f) ---".format(res.similarity))
                            println("📄 Файл: ${res.chunk.metadata.sourceFile}")
                            println("📝 Текст:\n${res.chunk.text}")
                        }
                    }
                }
            }
            "stats" -> {
                if (currentIndex == null) println("Индекс пуст")
                else println("📊 В индексе ${currentIndex!!.chunks.size} чанков. Создан: ${currentIndex!!.createdAt}")
            }
            "exit" -> {
                println("До свидания! 👋")
                isRunning = false
            }
            "help" -> printHelp()
            else -> println("Неизвестная команда. Введите 'help'")
        }
    }
}

fun printBanner() {
    println("""
        =============================================
           🤖 KOTLIN RAG CONSOLE - AI ADVENT 🎄
        =============================================
    """.trimIndent())
}

fun printHelp() {
    println("""
        Команды:
        • index <path>  - Индексировать папку с .md/.txt файлами
        • search <text> - Семантический поиск по базе
        • stats         - Показать статистику индекса
        • exit          - Выход
    """.trimIndent())
}
