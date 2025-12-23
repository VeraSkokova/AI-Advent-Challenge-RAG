# 💾 AI Advent Challenge — День 10: Внешняя Память

## Описание проекта

**Репозиторий:** [AI-Advent-Challenge-0/tree/day10](https://github.com/VeraSkokova/AI-Advent-Challenge-0/tree/day10)  
**Цель:** Реализовать сохранение контекста диалога между сессиями для чат-бота с YandexGPT

## Техническая реализация

### Хранилище данных
- **Файл:** `chat_history.json` в корне проекта
- **Формат:** JSON-сериализация списка сообщений (`List<Message>`)
- **Библиотека:** `kotlinx.serialization`

### Структура проекта

```
src/main/kotlin/ru/skokova/chatwithygpt/
├── client/
│   ├── LlmClient.kt              # Интерфейс для LLM клиентов
│   ├── YandexGptClient.kt        # Клиент для YandexGPT API
│   └── UniversalGptClient.kt     # Универсальный клиент
├── models/
│   ├── Message.kt                # Модель сообщения (role, text)
│   ├── GptRequest.kt             # Запрос к API
│   ├── GptResponse.kt            # Ответ от API
│   └── ApiResponse.kt            # Обёртка для ответов
├── data/
│   └── Persona.kt                # Персоны для разных режимов
├── config/
│   └── ApiConfig.kt              # Конфигурация API ключей
└── main.kt
```

## Ключевые компоненты

### 1. YandexGptClient

**Параметры запроса:**
- `modelUri`: `gpt://{folderId}/yandexgpt/{modelVersion}`
- `temperature`: 0.6 (по умолчанию)
- `maxTokens`: 1000
- `stream`: false

**Методы:**
```kotlin
suspend fun sendMessage(
    messagesHistory: List<Message>,
    persona: Persona
): Result<Pair<String, Int>>
```

### 2. Система персон (Persona)

**Поля Persona:**
- `systemPrompt`: String — системный промпт для модели
- `temperature`: Double — температура генерации
- `userMessageFormatter`: (String) -> String — форматтер входящих сообщений

**Доступные персоны:**
- Дефолтная (обычный чат)
- JSON-режим (структурированные ответы)
- Кастомные персоны

### 3. История диалога

**Формат сохранения:**
```json
[
  {"role": "user", "text": "Привет!"},
  {"role": "assistant", "text": "Здравствуйте! Чем могу помочь?"}
]
```

**Оптимизация:**
- Вместо сырых JSON-ответов сохраняется отформатированный текст
- Было: `{"role": "assistant", "text": "{\"type\": \"response\", \"text\": \"Привет\"}"}`
- Стало: `{"role": "assistant", "text": "Привет"}`

### 4. Восстановление контекста

При старте приложения:
1. Загружается файл `chat_history.json`
2. Показывается краткая сводка (последние 2 сообщения)
3. Диалог продолжается с учётом предыдущего контекста

**Пример вывода:**
```
📜 Restored context:
   USER: Напомни, о каком языке мы говорили...
   ASSISTANT: Мы говорили о языке программирования Котлин...
```

## Конфигурация

**Переменные окружения / local.properties:**
```properties
YANDEX_API_KEY=AQVN...
YANDEX_FOLDER_ID=b1g...
```

**Используемая модель:**
- YandexGPT Pro (`yandexgpt/latest`)
- URI: `gpt://{folderId}/yandexgpt/latest`

## Зависимости

```kotlin
// Ktor для HTTP клиента
implementation("io.ktor:ktor-client-core:3.0.0")
implementation("io.ktor:ktor-client-cio:3.0.0")
implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")

// Kotlinx Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
```

## Результат

Агент обладает **долгосрочной памятью**:
- Можно закрыть приложение и открыть снова
- Диалог продолжается с того же места
- Контекст и накопленные знания сохраняются

## Команды

Нет специальных команд — это консольный чат-бот:
```bash
./gradlew run
# Начинайте вводить сообщения
```
