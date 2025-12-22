package ru.skokova.aiadventchallenge.rag.services

import org.slf4j.LoggerFactory
import ru.skokova.aiadventchallenge.rag.config.Config
import ru.skokova.aiadventchallenge.rag.models.ChunkMetadata
import ru.skokova.aiadventchallenge.rag.models.DocumentChunk
import java.util.UUID

class TextChunker {
    private val logger = LoggerFactory.getLogger(TextChunker::class.java)

    fun chunkDocument(fileName: String, text: String): List<DocumentChunk> {
        // 1. Разбиваем на предложения (используем Lookbehind regex, чтобы оставить знак препинания)
        // Разделители: . ! ? и перевод строки
        val sentenceRegex = Regex("(?<=[.!?\\n])\\s+")
        val sentences = text.split(sentenceRegex).filter { it.isNotBlank() }

        val chunks = mutableListOf<DocumentChunk>()
        var currentChunkText = StringBuilder()
        var currentChunkStart = 0
        var currentChunkIndex = 0

        // Простой алгоритм скользящего окна
        var i = 0
        while (i < sentences.size) {
            val sentence = sentences[i]

            // Если добавление предложения превысит лимит и чанк не пустой
            if (currentChunkText.length + sentence.length > Config.MAX_CHUNK_SIZE && currentChunkText.isNotEmpty()) {
                // Сохраняем текущий чанк
                chunks.add(createChunk(fileName, currentChunkText.toString(), currentChunkIndex, currentChunkStart))
                currentChunkIndex++

                // Очищаем билдер
                currentChunkText.clear()

                // Backtrack (Отступаем назад для Overlap)
                // Ищем точку старта overlap'а
                var overlapSize = 0
                var backTrackIndex = i - 1
                val overlapBuffer = StringBuilder()

                // Набираем предложения с конца назад, пока не наберем ~100 символов
                while (backTrackIndex >= 0 && overlapSize < Config.CHUNK_OVERLAP) {
                    val prevSentence = sentences[backTrackIndex]
                    overlapBuffer.insert(0, "$prevSentence ")
                    overlapSize += prevSentence.length + 1
                    backTrackIndex--
                }

                currentChunkText.append(overlapBuffer)
                // Корректируем start position для следующего чанка (приблизительно)
                currentChunkStart += (chunks.last().text.length - overlapSize)
            }

            currentChunkText.append(sentence).append(" ")
            i++
        }

        // Добавляем последний хвост
        if (currentChunkText.isNotEmpty()) {
            chunks.add(createChunk(fileName, currentChunkText.toString(), currentChunkIndex, currentChunkStart))
        }

        // Обновляем метаданные totalChunks (так как мы их узнали только сейчас)
        return chunks.map { it.copy(metadata = it.metadata.copy(totalChunksInFile = chunks.size)) }
    }

    private fun createChunk(fileName: String, text: String, index: Int, startPos: Int): DocumentChunk {
        return DocumentChunk(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            embedding = emptyList(), // Эмбеддинг заполним позже
            metadata = ChunkMetadata(
                sourceFile = fileName,
                chunkIndex = index,
                totalChunksInFile = 0, // Заглушка
                startPosition = startPos,
                endPosition = startPos + text.length
            )
        )
    }
}
