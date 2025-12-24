package ru.skokova.aiadventchallenge.rag

import kotlinx.coroutines.runBlocking
import ru.skokova.aiadventchallenge.rag.client.YandexEmbeddingClient
import ru.skokova.aiadventchallenge.rag.client.YandexGptClient
import ru.skokova.aiadventchallenge.rag.config.Config
import ru.skokova.aiadventchallenge.rag.models.ComparisonReport
import ru.skokova.aiadventchallenge.rag.services.IndexService
import ru.skokova.aiadventchallenge.rag.services.RagComparisonService
import ru.skokova.aiadventchallenge.rag.services.RerankingComparisonService
import ru.skokova.aiadventchallenge.rag.services.RerankingService
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
    // Инициализация сервисов реранкинга
    val rerankingService = RerankingService(gptClient)
    val comparisonService = RerankingComparisonService(searchService, rerankingService, gptClient)

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
            "rerank" -> {
                if (currentIndex == null) {
                    println("❌ Индекс не загружен. Выполните 'index <путь>' или 'load'")
                    continue
                }

                print("🔍 Введите запрос: ")
                val query = readln()

                print("🎚️ Введите порог (по умолчанию 0.35): ")
                val thresholdInput = readln()
                val threshold = thresholdInput.toDoubleOrNull() ?: 0.35

                println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                println("🔍 Поиск с реранкингом (threshold: $threshold)...")

                // 1. Базовый поиск
                val searchResults = searchService.search(query, currentIndex, topK = 5)

                // 2. Threshold фильтрация
                val thresholdResults = rerankingService.filterByThreshold(searchResults, threshold)

                println("\n--- Результаты threshold фильтрации ---")
                if (thresholdResults.none { it.isRelevant }) {
                    println("⚠️ Нет результатов выше порога $threshold")
                } else {
                    thresholdResults.filter { it.isRelevant }.forEachIndexed { idx, result ->
                        println("${idx + 1}. [${result.chunk.metadata.sourceFile}] Score: %.4f".format(result.originalSimilarity))
                    }
                }
                println("Отфильтровано: ${thresholdResults.count { !it.isRelevant }} чанков")

                // 3. LLM реранкинг
                println("\n--- LLM реранкинг (может занять время) ---")
                val llmResults = rerankingService.rerankWithLLM(query, searchResults, threshold)

                if (llmResults.none { it.isRelevant }) {
                    println("⚠️ Нет результатов выше порога $threshold после LLM оценки")
                } else {
                    llmResults.filter { it.isRelevant }.forEachIndexed { idx, result ->
                        println("${idx + 1}. [${result.chunk.metadata.sourceFile}] " +
                                "Combined Score: %.4f (orig: %.4f)".format(result.rerankScore, result.originalSimilarity))
                    }
                }
            }

            "compare-rerank" -> {
                if (currentIndex == null) {
                    println("❌ Индекс не загружен")
                    continue
                }

                print("🎚️ Введите порог (по умолчанию 0.35): ")
                val thresholdInput = readln()
                val threshold = thresholdInput.toDoubleOrNull() ?: 0.35

                println("\n🔬 Запускаю сравнение методов реранкинга...")
                println("⏱️ Это займёт несколько минут (10 вопросов × 3 метода)...")

                val testQuestions = listOf(
                    // 1. Проверка дистрактора (chunking): Векторный поиск может найти кулинарию, LLM должна отфильтровать
                    "Что такое chunking и как он помогает в RAG?",

                    // 2. Специфический факт: Требует точного совпадения параметров, LLM должна поднять документацию выше
                    "Какие параметры modelUri и temperature используются по умолчанию в YandexGPT?",

                    // 3. Сравнение (Reasoning): Ответ собирается из нескольких кусков, LLM должна оставить оба
                    "Чем отличается RAG от Fine-tuning и когда что использовать?",

                    // 4. Ловушка "Векторы": Векторный поиск любит слово "вектор", но вопрос про физический смысл
                    "Объясни геометрический смысл косинусного сходства векторов",

                    // 5. Технический вопрос: Проверка понимания архитектуры
                    "Зачем нужен overlap между чанками и какой размер рекомендуются?",

                    // 6. Вопрос с подвохом (Self-attention): Если этого нет в базе, LLM должна честно дать 0.0
                    "Как работает механизм self-attention в трансформерах?",

                    // 7. Практический вопрос: Векторный поиск может найти теорию, LLM должна найти практику
                    "Какие конкретно модели эмбеддингов лучше брать для русского языка локально?",

                    // 8. Вопрос про деньги/лимиты (если есть в доке): Важно для точного поиска
                    "Какие ограничения на количество токенов есть в YandexGPT API?",

                    // 9. Обобщение: Требует понимания всего пайплайна
                    "Опиши полный алгоритм работы RAG системы по шагам",

                    // 10. Абстрактный вопрос: Проверка на "галлюцинации" реранкера
                    "Почему векторные базы данных называют семантическим поиском?"
                )

                val comparisons = comparisonService.runComparison(
                    testQuestions,
                    currentIndex,
                    threshold
                )

                comparisonService.saveReport(comparisons)

                println("\n✅ Сравнение завершено!")
                println("📄 Отчёт сохранён в RERANKING_REPORT.md")
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
