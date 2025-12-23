# 🎄 AI Advent Challenge — Day 17: RAG System + Comparison

**Проект:** Консольная система RAG (Retrieval Augmented Generation) на Kotlin с функцией сравнения RAG vs Без-RAG.  
**Задача:** Реализовать индексацию документов, семантический поиск и автоматическую оценку эффективности RAG.

---

## 🎯 Основные фичи

### 1. 📚 RAG Система
- **Sentence-aware Chunking**: разбиение текста по границам предложений
- **Семантический поиск**: косинусное сходство векторов
- **Yandex Embeddings API**: `text-search-doc` и `text-search-query` модели
- **Персистентность**: сохранение индекса в `index.json`

### 2. 🔬 Сравнение RAG vs Без-RAG
- **Автоматическое сравнение**: одинаковые вопросы к YandexGPT с RAG и без него
- **Умный анализ**: определение случаев, когда RAG действительно помогает
- **Markdown отчёт**: подробный анализ в `COMPARISON_REPORT.md`
- **Консольные логи**: вид ответов в реальном времени

### 3. 🛠️ Надёжность
- **Retry-механизм**: экспоненциальная задержка при Rate Limits
- **Обработка ошибок**: timeout, сетевые сбои, некорректные JSON
- **Логирование**: SLF4J + Logback для отладки

---

## 🛠️ Технологический стек

| Компонент | Версия | Назначение |
|-----------|---------|-------------|
| **Kotlin** | 2.2.20 | Основной язык |
| **Ktor Client** | 3.0.0 | HTTP клиент (CIO engine) |
| **Coroutines** | 1.9.0 | Асинхронность |
| **Kotlinx Serialization** | 1.6.0 | JSON сериализация |
| **Yandex Foundation Models** | latest | Embeddings + GPT API |

---

## 🚀 Быстрый старт

### 1️⃣ Настройка API

Создайте файл `local.properties` в корне проекта:

```properties
yandex.api.key=AQVN...
yandex.folder.id=b1g...
```

### 2️⃣ Запуск

```bash
./gradlew run --console=plain
```

### 3️⃣ Команды

```bash
# Индексировать документы
> index documents/

# Поиск по базе знаний
> search что такое chunking?

# Сравнение RAG vs без-RAG
> compare

# Статистика
> stats
```

---

## 📊 Функция сравнения (compare)

### 🎯 Что она делает?

Команда `compare` задаёт **одинаковые вопросы** YandexGPT в двух режимах:

1. **С RAG** — модель получает контекст из найденных чанков
2. **Без RAG** — модель отвечает только на основе своих знаний

Затем **система автоматически анализирует**:
- Качество ответов (конкретика, точность)
- Score чанков (косинусное сходство)
- Наличие ссылок на источники

### 📄 Результат

Генерируется **подробный отчёт** `COMPARISON_REPORT.md` с:
- ✅ / ❌ Ответами с RAG и без RAG
- 📚 Использованными чанками (с score)
- 🔍 Анализом различий
- 📈 Общей статистикой

### 💡 Когда RAG помогает?

RAG считается эффективным, когда:

✅ **Модель без RAG** не знает ответа («нет информации», «к сожалению»)  
✅ **Модель с RAG** даёт конкретный ответ со ссылками на источники  
✅ **Score чанков** > 0.35 (найдена релевантная информация)

**Примеры хороших вопросов:**
- «Какие параметры используются в YandexGptClient в проекте Day 10?»
- «В каком файле сохраняется история диалога?»
- «Какой API использует CryptoCurrencyMCPServer?»

→ Подробнее см. [`RAG_IMPROVEMENT_GUIDE.md`](RAG_IMPROVEMENT_GUIDE.md)

---

## 📚 Структура проекта

```
src/main/kotlin/ru/skokova/aiadventchallenge/rag/
├── client/
│   ├── YandexEmbeddingClient.kt      # Клиент для embeddings API
│   └── YandexGptClient.kt            # Клиент для generation API
├── services/
│   ├── IndexService.kt               # Индексация документов
│   ├── SearchService.kt              # Семантический поиск
│   ├── TextChunker.kt                # Sentence-aware chunking
│   └── RagComparisonService.kt       # 🆕 Сравнение RAG vs без-RAG
├── models/
│   ├── DomainModels.kt               # Chunk, VectorIndex, SearchResult
│   ├── GptModels.kt                  # API модели
│   └── Comparison.kt                 # 🆕 RagAnswer, ComparisonReport
├── config/
│   └── Config.kt                     # Загрузка API ключей
└── Main.kt                           # Консольное приложение
```

---

## 📝 Пример работы

### 🔍 Семантический поиск

```bash
> search зачем нужен overlap?
🔍 Ищу: "зачем нужен overlap?"...

--- Результат #1 (Score: 0.8412) ---
📄 Файл: project_day17_info.md
📝 Текст:
Overlap (перекрытие) 100 символов необходим для сохранения 
контекста на границах чанков...
```

### 🔬 Сравнение RAG vs Без-RAG

```bash
> compare
🔬 Запускаю сравнение RAG vs без-RAG...

================================================================================
📝 Вопрос 1/7: "Какие параметры используются в YandexGptClient в проекте Day 10?"
================================================================================

🔍 Генерация ответа С RAG...
📚 Найденные чанки:
  1. [project_day10_info.md] (Score: 0.4478)
  2. [project_day17_info.md] (Score: 0.4609)
  3. [project_day10_info.md] (Score: 0.4457)

✅ Ответ С RAG:
В YandexGptClient в проекте Day 10 используются следующие параметры:
- temperature: 0.6
- maxTokens: 1000
- stream: false
[источник: project_day10_info.md]

================================================================================

🤔 Генерация ответа БЕЗ RAG...

❌ Ответ БЕЗ RAG:
К сожалению, у меня нет информации о проекте Day 10...

================================================================================

✅ Результаты сохранены в COMPARISON_REPORT.md
```

---

## ✅ Критерии выполнения

- [x] **Sentence-aware Chunking** — разбиение по границам предложений
- [x] **Yandex Embeddings API** — `text-search-doc` и `text-search-query`
- [x] **Семантический поиск** — косинусное сходство
- [x] **Персистентность** — сохранение индекса
- [x] **🆕 Сравнение RAG vs Без-RAG** — автоматическая оценка эффективности
- [x] **🆕 Умный анализ** — определение случаев, когда RAG помогает
- [x] **🆕 Markdown отчёт** — подробный анализ в `COMPARISON_REPORT.md`
- [x] **Retry-механизм** — обработка Rate Limits
- [x] **Логирование** — SLF4J + консольные логи

---

## 📂 Дополнительные файлы

- [`COMPARISON_REPORT.md`](COMPARISON_REPORT.md) — результаты сравнения (генерируется командой `compare`)
- [`RAG_IMPROVEMENT_GUIDE.md`](RAG_IMPROVEMENT_GUIDE.md) — рекомендации по улучшению RAG
- [`documents/`](documents/) — примеры документов для индексации

---

## 👥 Автор

**Vera Skokova** ([@VeraSkokova](https://github.com/VeraSkokova))  
**Дата:** 24.12.2025  

*Разработано в рамках AI Advent Challenge* 🎄
