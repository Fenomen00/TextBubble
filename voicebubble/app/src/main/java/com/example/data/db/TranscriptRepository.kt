package com.example.data.db

import kotlinx.coroutines.flow.Flow

class TranscriptRepository(private val dao: TranscriptDao) {
    val allTranscripts: Flow<List<TranscriptEntity>> = dao.getAllTranscripts()

    suspend fun insert(rawText: String, cleanedText: String, wasCleaned: Boolean): Long {
        return dao.insertTranscript(
            TranscriptEntity(
                rawText = rawText,
                cleanedText = cleanedText,
                wasCleaned = wasCleaned
            )
        )
    }

    suspend fun deleteById(id: Long) = dao.deleteTranscript(id)

    suspend fun clearAll() = dao.clearAll()
}
