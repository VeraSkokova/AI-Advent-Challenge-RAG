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
                    
                    // Вопросы смешанные: общие + специфичные для проектов
                    val questions = listOf(
                        // Про Day 10 (чат-бот с памятью)
                        "Какие параметры используются в YandexGptClient в проекте Day 10?",
                        
                        // Про Day 15 (MCP оркестрация)
                        "Какие 4 MCP сервера используются в проекте Day 15 и какие у них роли?",
                        
                        // Про Day 17 (RAG система)
                        "Какой размер чанка и overlap используется в TextChunker в проекте Day 17?",
                        
                        // Общие вопросы про RAG
                        "В чём разница между text-search-doc и text-search-query моделями?",
                        
                        // Специфичный вопрос про Day 15
                        "Какой API использует CryptoCurrencyMCPServer для получения курсов криптовалют?",
                        
                        // Специфичный вопрос про Day 10
                        "В каком файле сохраняется история диалога в проекте Day 10?",
                        
                        // Специфичный вопрос про Day 17
                        "Какие консольные команды доступны в RAG проекте Day 17?"
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
            
            // Ответ С RAG
            appendLine("### ✅ Ответ С RAG")
            appendLine()
            appendLine("**Использованные чанки:**")
            appendLine()
            comparison.answerWithRag.chunks.forEachIndexed { i, chunk ->
                appendLine("${i + 1}. **[${chunk.sourceFile}]** (Score: %.4f)".format(chunk.score))
                // Умное обрезание: до 100 символов, но по границе слова
                val preview = chunk.text
                    .take(100)
                    .let { truncated ->
                        if (chunk.text.length > 100) {
                            // Находим последний пробел, чтобы не обрезать слово посередине
                            val lastSpace = truncated.lastIndexOf(' ')
                            if (lastSpace > 50) truncated.substring(0, lastSpace) else truncated
                        } else truncated
                    }
                    .replace("\n", " ")  // Убираем переносы строк
                    .replace("\r", "")
                appendLine("   > “$preview...”")
                appendLine()
            }
            
            appendLine("**Ответ модели:**")
            appendLine()
            appendLine(comparison.answerWithRag.answer)
            appendLine()
            
            // Ответ БЕЗ RAG
            appendLine("### ❌ Ответ БЕЗ RAG")
            appendLine()
            appendLine(comparison.answerWithoutRag)
            appendLine()
            
            // Анализ
            appendLine("### 🔍 Анализ различий")
            appendLine()
            appendLine(comparison.analysis)
            appendLine()
            appendLine("---")
            appendLine()
        }
        
        // Summary
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
