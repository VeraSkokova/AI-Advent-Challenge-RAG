# 🎄 AI Advent Challenge — День 15: MCP Orchestration

## Описание проекта

**Репозиторий:** [AI-Advent-Challenge-MCP/tree/day15](https://github.com/VeraSkokova/AI-Advent-Challenge-MCP/tree/day15)  
**Цель:** Реализовать оркестрацию нескольких независимых MCP серверов

## Архитектура

Система состоит из **4 компонентов**, работающих совместно:

### 1. CryptoCurrencyMCPServer (Kotlin, In-Process)

**Роль:** Источник данных  
**Tool:** `check_crypto_rates`  
**Функционал:** Получает реальные курсы криптовалют через **CoinCap API**

**Пример запроса:**
```json
{
  "name": "check_crypto_rates",
  "arguments": {
    "symbols": ["BTC", "ETH"]
  }
}
```

**Ответ:**
```json
{
  "BTC": {
    "symbol": "BTC",
    "name": "Bitcoin",
    "priceUsd": "42850.30",
    "changePercent24Hr": "2.45"
  },
  "ETH": { ... }
}
```

### 2. SummarizationMCPServer (Kotlin, In-Process)

**Роль:** Обработка данных (Business Logic)  
**Tool:** `summarize_data`  
**Функционал:** Превращает сырой JSON в красивый текстовый отчёт (**без использования LLM**)

**Особенности:**
- Работает чисто на Kotlin (не тратит токены)
- Форматирует данные в читаемый вид
- Может обрабатывать JSON любой структуры

### 3. ReminderMCPServer (Kotlin, In-Process)

**Роль:** Управление состоянием (Legacy)  
**Tools:**
- `add_reminder` — добавить напоминание
- `list_reminders` — показать все напоминания
- `remove_reminder` — удалить напоминание

**Хранилище:** In-memory (во время работы приложения)

### 4. Filesystem Server (Node.js, External Process)

**Роль:** Ввод/Вывод (Side Effects)  
**Tool:** `write_file`  
**Функционал:** Сохраняет результаты на диск  
**Запуск:** Через `npx @modelcontextprotocol/server-filesystem`

**Папка вывода:** `./mcp-output/`

## Принцип работы

**YandexAIAgent** — дирижёр, который:
1. Собирает список инструментов от всех серверов
2. Формирует System Prompt с описанием доступных tools
3. Выполняет их в нужной последовательности (**Chain of Thought / Pipeline**)

## Пример сценария (Demo)

**Пользователь:**
```
Проверь курсы BTC и ETH, сформируй отчет и сохрани его в файл crypto_report.txt
```

**Цепочка вызовов:**
1. Агент вызывает `check_crypto_rates` (`crypto-server`)
2. Получает JSON с курсами
3. Агент вызывает `summarize_data` (`summarization-server`)
4. Получает текстовый отчёт
5. Агент вызывает `write_file` (`filesystem-server`)
6. Файл сохраняется в `./mcp-output/crypto_report.txt`

## Структура проекта

```
src/main/kotlin/ru/skokova/chatwithygpt/
├── mcp/
│   ├── McpServer.kt               # Базовый интерфейс MCP сервера
│   ├── CryptoCurrencyMCPServer.kt # Сервер для CoinCap API
│   ├── SummarizationMCPServer.kt  # Сервер для форматирования
│   ├── ReminderMCPServer.kt       # Сервер напоминаний
│   └── StdioServerTransport.kt    # Transport для Node.js серверов
├── agent/
│   └── YandexAIAgent.kt           # Оркестратор MCP серверов
├── client/
│   └── YandexGptClient.kt        # Клиент для YandexGPT
└── main.kt
```

## Технологии

### MCP SDK
```kotlin
implementation("io.modelcontextprotocol:kotlin-sdk:0.7.4")
```

### Yandex GPT
- **Модель:** YandexGPT Pro (`yandexgpt/latest`)
- **Function Calling:** Поддержка вызова функций через `functionCall` в ответе

### CoinCap API
- **Endpoint:** `https://api.coincap.io/v2/assets`
- **Бесплатный доступ** (без API key)

### Stdio Transport
- Используется для коммуникации с Node.js процессом
- Обмен данными через stdin/stdout

## Конфигурация

**Переменные окружения:**
```bash
export YANDEX_API_KEY="<ваши_данные>"
export YANDEX_FOLDER_ID="<ваши_данные>"
```

**Windows (PowerShell):**
```powershell
$env:YANDEX_API_KEY="<ваши_данные>"
$env:YANDEX_FOLDER_ID="<ваши_данные>"
```

## Запуск

```bash
./gradlew run
```

**Предварительные требования:**
- JDK 17+
- Node.js (для npx)
- Yandex Cloud API Key

## Особенности реализации

### In-Process серверы (Kotlin)
- Работают в одном процессе с агентом
- Быстрый обмен данными (без сетевых задержек)

### External Process серверы (Node.js)
- Запускаются как отдельные процессы
- Коммуникация через Stdio Transport
- Используются для Side Effects (файловые операции)

## Результат

Агент умеет:
- ✅ Оркестрировать несколько MCP серверов
- ✅ Выполнять цепочки действий (Chain of Thought)
- ✅ Работать с разными источниками данных (API, файлы, in-memory)
- ✅ Динамически обнаруживать доступные инструменты
