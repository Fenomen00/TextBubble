package com.example.model

/**
 * Supported AI Providers.
 * Built extensibly so providers like Gemini, OpenAI, etc. can be added seamlessly
 * without changing the core settings UI architecture.
 */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val isEnabled: Boolean = true
) {
    GROQ(
        id = "groq",
        displayName = "Groq",
        subtitle = "Ultra-fast inference (LPU Engine)",
        isEnabled = true
    ),
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        subtitle = "Multimodal AI by Google (Coming Soon)",
        isEnabled = false
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        subtitle = "GPT & Whisper (Coming Soon)",
        isEnabled = false
    );

    companion object {
        fun fromId(id: String): AiProvider {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: GROQ
        }

        fun availableProviders(): List<AiProvider> = entries
    }
}
