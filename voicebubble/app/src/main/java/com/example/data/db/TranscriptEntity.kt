package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawText: String,
    val cleanedText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val wasCleaned: Boolean = true
)
