# 🎄 AI Advent Challenge — День 17: RAG System

## Описание проекта

**Репозиторий:** [AI-Advent-Challenge-RAG/tree/day17](https://github.com/VeraSkokova/AI-Advent-Challenge-RAG/tree/day17)  
**Цель:** Реализовать консольную систему RAG (Retrieval Augmented Generation) на Kotlin

## Технологический стек

- **Язык:** Kotlin 2.2.20 (JVM)
- **Сетевой клиент:** Ktor Client 3.0.0 (CIO engine)
- **Асинхронность:** Kotlin Coroutines 1.9.0
- **Сериализация:** Kotlinx Serialization 1.6.0
- **AI API:** Yandex Foundation Models (Embeddings + GPT)

## Основные возможности

### 1. Умный Чанкинг (Smart Chunking)

**Алгоритм:** `Sentence-aware Sliding Window`

**Параметры TextChunker:**
- **Chunk size:** 512 символов
- **Overlap:** 100 символов (для сохранения контекста на стыках)
- **Min chunk size:** 50 символов

**Особенности:**
- Текст разбивается **по границам предложений**, а не просто по символам
- Перекрытие (overlap) сохраняет контекст между чанками

### 2. Векторизация (Embeddings)

**Используемые модели Yandex:**

1. **text-search-doc** — для документов
   - URI: `emb://{folderId}/text-search-doc/latest`
   - Размер вектора: 256 измерений
   - Используется при индексации чанков

2. **text-search-query** — для поисковых запросов
   - URI: `emb://{folderId}/text-search-query/latest`
   - Размер вектора: 256 измерений
   - Используется при поиске

**Особенности:**
- Реализован **Retry-механизм** с экспоненциальной задержкой
- Обработка Rate Limits от Yandex API

### 3. Семантический поиск

**Метрика:** Косинусное сходство (Cosine Similarity)

**Формула:**
```
similarity = (A · B) / (||A|| * ||B||)
```

**Алгоритм:**
1. Запрос преобразуется в вектор (`text-search-query`)
2. Вычисляется косинусное сходство со всеми чанками
3. Результаты сортируются по убыванию сходства
4. Возвращаются top-K результатов (по умолчанию K=3)

### 4. Персистентность

**Хранилище:** `index.json` в корне проекта

**Формат:**
```json
{
  "createdAt": "23.12.2025 21:30",
  "chunks": [
    {
      "text": "Текст чанка...",
      "embedding": [0.123, -0.456, ...],
      "metadata": {
        "sourceFile": "document.md",
        "chunkIndex": 0
      }
    }
  ]
}
```

**Функции:**
- `saveIndex()` — сохранение индекса
- `loadIndex()` — загрузка существующего индекса

### 5. Сравнение RAG vs БЕЗ-RAG

**RagComparisonService** выполняет:
1. Генерацию ответа **с RAG** (с использованием контекста из чанков)
2. Генерацию ответа **без RAG** (только на основе знаний модели)
3. Анализ различий (точность, конкретика, score)
4. Создание отчёта `COMPARISON_REPORT.md`

**Параметры YandexGPT:**
- **Модель:** `yandexgpt/latest`
- **Temperature:** 0.3 (для более детерминированных ответов)
- **MaxTokens:** 2000

## Структура проекта

```
src/main/kotlin/ru/skokova/aiadventchallenge/rag/
├── client/
│   ├── YandexEmbeddingClient.kt  # Клиент для embeddings
│   └── YandexGptClient.kt        # Клиент для генерации
├── services/
│   ├── IndexService.kt           # Создание и управление индексом
│   ├── SearchService.kt          # Семантический поиск
│   ├── TextChunker.kt            # Разбиение на чанки
│   └── RagComparisonService.kt   # Сравнение RAG vs без-RAG
├── models/
│   ├── DomainModels.kt           # Chunk, VectorIndex, SearchResult
│   ├── GptModels.kt              # API модели (запросы/ответы)
│   └── Comparison.kt             # Модели для отчётов
├── config/
│   └── Config.kt                 # Конфигурация API
└── Main.kt
```

## Консольные команды

### index <path>
Индексировать папку с `.md` или `.txt` файлами

**Пример:**
```bash
> index documents/
```

### search <text>
Семантический поиск по индексу

**Пример:**
```bash
> search что такое chunking?
```

### compare
Запустить сравнение RAG vs без-RAG с предопределёнными вопросами

**Пример:**
```bash
> compare
```

### stats
Показать статистику индекса

**Пример:**
```bash
> stats
📊 В индексе 157 чанков. Создан: 23.12.2025 21:30
```

### exit
Выход из приложения

## Конфигурация

**Файл `local.properties`:**
```properties
yandex.api.key=AQVN...
yandex.folder.id=b1g...
```

**Endpoints:**
- Embeddings API: `https://llm.api.cloud.yandex.net/foundationModels/v1/textEmbedding`
- GPT API: `https://llm.api.cloud.yandex.net/foundationModels/v1/completion`

## Пример работы

```bash
> index documents/
🚀 Начинаю индексацию папки: documents/
[1/3] Обработка файла: rag_llm_source_clean.txt
[2/3] Обработка файла: ai_lecture_transcript.md
[3/3] Обработка файла: lesson_1.txt
✅ Индексация завершена успешно!

> search зачем нужен overlap?
🔍 Ищу: "зачем нужен overlap?"...

--- Результат #1 (Score: 0.8412) ---
📄 Файл: rag_llm_source_clean.txt
📝 Текст:
Overlap (перекрытие) необходим для того, чтобы не терять контекст на границах чанков...
```

## Особенности реализации

### Retry-механизм
При ошибках API (429 Too Many Requests):
1. Первая попытка: ждём 1 секунду
2. Вторая попытка: ждём 2 секунды
3. Третья попытка: ждём 4 секунды

### Cosine Similarity
Реализована чисто на Kotlin без внешних библиотек:
```kotlin
fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
    val dotProduct = a.zip(b).sumOf { it.first * it.second }
    val normA = sqrt(a.sumOf { it * it })
    val normB = sqrt(b.sumOf { it * it })
    return dotProduct / (normA * normB)
}
```

## Запуск

```bash
./gradlew run --console=plain
```

**Требования:**
- JDK 17+
- Yandex Cloud API Key
- Файл `local.properties` с ключами
