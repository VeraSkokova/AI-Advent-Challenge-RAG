package ru.skokova.aiadventchallenge.rag

import kotlinx.coroutines.runBlocking
import ru.skokova.aiadventchallenge.rag.client.YandexEmbeddingClient
import ru.skokova.aiadventchallenge.rag.client.YandexGptClient
import ru.skokova.aiadventchallenge.rag.config.Config
import ru.skokova.aiadventchallenge.rag.models.ComparisonReport
import ru.skokova.aiadventchallenge.rag.services.IndexService
import ru.skokova.aiadventchallenge.rag.services.RagComparisonService
import ru.skokova.aiadventchallenge.rag.services.SearchService
import ru.skokova.aiadventchallenge.rag.services.TextChunker
import java.io.File
import java.util.Scanner

fun main() = runBlocking {
    printBanner()

    // Инициализация компонентов
    val embeddingClient = YandexEmbeddingClient()
    val gptClient = YandexGptClient()
    val chunker = TextChunker()
    val indexService = IndexService(embeddingClient, chunker)
    val searchService = SearchService(embeddingClient)
    val ragComparisonService = RagComparisonService(searchService, gptClient)

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
            "compare" -> {
                if (currentIndex == null) {
                    println("⚠️ Сначала создайте индекс!")
                } else {
                    println("🔬 Запускаю сравнение RAG vs без-RAG...")
                    val questions = listOf(
                        "Что такое RAG и зачем он нужен?",
                        "Какие основные ограничения у LLM моделей?",
                        "Чем отличается embedding модель от генеративной?",
                        "Как работает косинусное сходство при поиске?",
                        "Что такое overlap в chunking и зачем он нужен?"
                    )
                    
                    try {
                        val report = ragComparisonService.compareApproaches(questions, currentIndex!!)
                        saveComparisonReport(report)
                        println("✅ Результаты сохранены в COMPARISON_REPORT.md")
                    } catch (e: Exception) {
                        println("❌ Ошибка при сравнении: ${e.message}")
                        e.printStackTrace()
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

fun saveComparisonReport(report: ComparisonReport) {
    val content = buildString {
        appendLine("# 🔬 Сравнение RAG vs БЕЗ-RAG — День 17")
        appendLine()
        appendLine("## 📅 Дата тестирования")
        appendLine(report.createdAt)
        appendLine()
        appendLine("---")
        appendLine()
        
        report.questions.forEachIndexed { index, comparison ->
            appendLine("## Вопрос ${index + 1}: \"${comparison.question}\"")
            appendLine()
            appendLine("### ✅ Ответ С RAG")
            appendLine("**Использованные чанки:**")
            comparison.answerWithRag.chunks.forEachIndexed { i, chunk ->
                appendLine("${i + 1}. **[${chunk.sourceFile}]** (Score: %.4f)".format(chunk.score))
                appendLine("   > \"${chunk.text.take(150)}...\"")
                appendLine()
            }
            appendLine("**Ответ модели:**")
            appendLine(comparison.answerWithRag.answer)
            appendLine()
            appendLine("### ❌ Ответ БЕЗ RAG")
            appendLine(comparison.answerWithoutRag)
            appendLine()
            appendLine("### 🔍 Анализ различий")
            appendLine(comparison.analysis)
            appendLine()
            appendLine("---")
            appendLine()
        }
        
        appendLine(report.summary)
    }
    
    File("COMPARISON_REPORT.md").writeText(content)
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
        • index <path>   - Индексировать папку с .md/.txt файлами
        • search <text>  - Семантический поиск по базе
        • compare        - Сравнить RAG vs без-RAG
        • stats          - Показать статистику индекса
        • exit           - Выход
    """.trimIndent())
}
