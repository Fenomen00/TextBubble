package com.example.model

/**
 * Settings for speech-to-text transcription.
 */
data class TranscriptionConfig(
    val provider: AiProvider = AiProvider.GROQ,
    val apiKey: String = "",
    val selectedModel: String = DEFAULT_TRANSCRIPTION_MODEL
) {
    companion object {
        const val DEFAULT_TRANSCRIPTION_MODEL = "whisper-large-v3-turbo"

        // Default speech models provided as fallback / initial state
        val DEFAULT_SPEECH_MODELS = listOf(
            "whisper-large-v3-turbo",
            "whisper-large-v3",
            "distil-whisper-large-v3-en"
        )
    }
}

/**
 * Settings for post-transcription text cleanup (grammar, filler word removal).
 */
data class CleanupConfig(
    val isEnabled: Boolean = true,
    val provider: AiProvider = AiProvider.GROQ,
    val apiKey: String = "",
    val selectedModel: String = DEFAULT_CLEANUP_MODEL,
    val instruction: String = DEFAULT_INSTRUCTION
) {
    companion object {
        const val DEFAULT_CLEANUP_MODEL = "llama-3.3-70b-versatile"
        const val DEFAULT_INSTRUCTION =
            "Fix grammar and remove filler words. Keep my natural, casual style. Do not rewrite or reword unless something is wrong."

        // Default chat/text models provided as fallback / initial state
        val DEFAULT_TEXT_MODELS = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768",
            "gemma2-9b-it"
        )
    }
}

/**
 * Consolidated settings state for the Voice Bubble app.
 * Can be read synchronously or observed via Flow by any future background service.
 */
data class VoiceBubbleConfig(
    val transcription: TranscriptionConfig = TranscriptionConfig(),
    val cleanup: CleanupConfig = CleanupConfig()
)
