package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AiProvider
import com.example.model.CleanupConfig
import com.example.model.TranscriptionConfig
import com.example.model.VoiceBubbleConfig
import com.example.security.SecureKeyStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Interface for reading and persisting Voice Bubble configuration.
 */
interface VoiceBubbleSettingsRepository {
    val configFlow: StateFlow<VoiceBubbleConfig>

    /**
     * Synchronous access for future background services and audio workers.
     */
    fun getConfig(): VoiceBubbleConfig

    suspend fun updateTranscriptionConfig(
        provider: AiProvider? = null,
        apiKey: String? = null,
        model: String? = null
    )

    suspend fun updateCleanupConfig(
        isEnabled: Boolean? = null,
        provider: AiProvider? = null,
        apiKey: String? = null,
        model: String? = null,
        instruction: String? = null
    )

    suspend fun resetCleanupInstruction()
}

/**
 * Implementation of VoiceBubbleSettingsRepository using SharedPreferences
 * and SecureKeyStorage (Android KeyStore AES-256-GCM) for sensitive API keys.
 */
class VoiceBubbleSettingsRepositoryImpl(
    context: Context,
    private val secureKeyStorage: SecureKeyStorage = SecureKeyStorage(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : VoiceBubbleSettingsRepository {

    companion object {
        private const val PREFS_NAME = "voice_bubble_settings"

        // Transcription keys
        private const val KEY_TRANSCRIPTION_PROVIDER = "transcription_provider"
        private const val KEY_TRANSCRIPTION_API_KEY_ENC = "transcription_api_key_enc"
        private const val KEY_TRANSCRIPTION_MODEL = "transcription_model"

        // Cleanup keys
        private const val KEY_CLEANUP_ENABLED = "cleanup_enabled"
        private const val KEY_CLEANUP_PROVIDER = "cleanup_provider"
        private const val KEY_CLEANUP_API_KEY_ENC = "cleanup_api_key_enc"
        private const val KEY_CLEANUP_MODEL = "cleanup_model"
        private const val KEY_CLEANUP_INSTRUCTION = "cleanup_instruction"
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfig())
    override val configFlow: StateFlow<VoiceBubbleConfig> = _configFlow.asStateFlow()

    override fun getConfig(): VoiceBubbleConfig {
        return _configFlow.value
    }

    private fun loadConfig(): VoiceBubbleConfig {
        return try {
            val transProviderId = prefs.getString(KEY_TRANSCRIPTION_PROVIDER, AiProvider.GROQ.id) ?: AiProvider.GROQ.id
            val transEncKey = prefs.getString(KEY_TRANSCRIPTION_API_KEY_ENC, "") ?: ""
            val transDecryptedKey = if (transEncKey.isNotEmpty()) secureKeyStorage.decrypt(transEncKey) else ""
            val transModel = prefs.getString(KEY_TRANSCRIPTION_MODEL, TranscriptionConfig.DEFAULT_TRANSCRIPTION_MODEL)
                ?: TranscriptionConfig.DEFAULT_TRANSCRIPTION_MODEL

            val cleanupEnabled = prefs.getBoolean(KEY_CLEANUP_ENABLED, true)
            val cleanupProviderId = prefs.getString(KEY_CLEANUP_PROVIDER, AiProvider.GROQ.id) ?: AiProvider.GROQ.id
            val cleanupEncKey = prefs.getString(KEY_CLEANUP_API_KEY_ENC, "") ?: ""
            val cleanupDecryptedKey = if (cleanupEncKey.isNotEmpty()) secureKeyStorage.decrypt(cleanupEncKey) else ""
            val cleanupModel = prefs.getString(KEY_CLEANUP_MODEL, CleanupConfig.DEFAULT_CLEANUP_MODEL)
                ?: CleanupConfig.DEFAULT_CLEANUP_MODEL
            val cleanupInstruction = prefs.getString(KEY_CLEANUP_INSTRUCTION, CleanupConfig.DEFAULT_INSTRUCTION)
                ?: CleanupConfig.DEFAULT_INSTRUCTION

            VoiceBubbleConfig(
                transcription = TranscriptionConfig(
                    provider = AiProvider.fromId(transProviderId),
                    apiKey = transDecryptedKey,
                    selectedModel = transModel
                ),
                cleanup = CleanupConfig(
                    isEnabled = cleanupEnabled,
                    provider = AiProvider.fromId(cleanupProviderId),
                    apiKey = cleanupDecryptedKey,
                    selectedModel = cleanupModel,
                    instruction = cleanupInstruction
                )
            )
        } catch (_: Exception) {
            VoiceBubbleConfig()
        }
    }

    override suspend fun updateTranscriptionConfig(
        provider: AiProvider?,
        apiKey: String?,
        model: String?
    ) {
        val current = _configFlow.value.transcription
        val newProvider = provider ?: current.provider
        val newApiKey = apiKey ?: current.apiKey
        val newModel = model ?: current.selectedModel

        val editor = prefs.edit()
        if (provider != null) {
            editor.putString(KEY_TRANSCRIPTION_PROVIDER, newProvider.id)
        }
        if (apiKey != null) {
            val encKey = if (newApiKey.isNotEmpty()) secureKeyStorage.encrypt(newApiKey) else ""
            editor.putString(KEY_TRANSCRIPTION_API_KEY_ENC, encKey)
        }
        if (model != null) {
            editor.putString(KEY_TRANSCRIPTION_MODEL, newModel)
        }
        editor.apply()

        _configFlow.value = _configFlow.value.copy(
            transcription = current.copy(
                provider = newProvider,
                apiKey = newApiKey,
                selectedModel = newModel
            )
        )
    }

    override suspend fun updateCleanupConfig(
        isEnabled: Boolean?,
        provider: AiProvider?,
        apiKey: String?,
        model: String?,
        instruction: String?
    ) {
        val current = _configFlow.value.cleanup
        val newEnabled = isEnabled ?: current.isEnabled
        val newProvider = provider ?: current.provider
        val newApiKey = apiKey ?: current.apiKey
        val newModel = model ?: current.selectedModel
        val newInstruction = instruction ?: current.instruction

        val editor = prefs.edit()
        if (isEnabled != null) {
            editor.putBoolean(KEY_CLEANUP_ENABLED, newEnabled)
        }
        if (provider != null) {
            editor.putString(KEY_CLEANUP_PROVIDER, newProvider.id)
        }
        if (apiKey != null) {
            val encKey = if (newApiKey.isNotEmpty()) secureKeyStorage.encrypt(newApiKey) else ""
            editor.putString(KEY_CLEANUP_API_KEY_ENC, encKey)
        }
        if (model != null) {
            editor.putString(KEY_CLEANUP_MODEL, newModel)
        }
        if (instruction != null) {
            editor.putString(KEY_CLEANUP_INSTRUCTION, newInstruction)
        }
        editor.apply()

        _configFlow.value = _configFlow.value.copy(
            cleanup = current.copy(
                isEnabled = newEnabled,
                provider = newProvider,
                apiKey = newApiKey,
                selectedModel = newModel,
                instruction = newInstruction
            )
        )
    }

    override suspend fun resetCleanupInstruction() {
        updateCleanupConfig(instruction = CleanupConfig.DEFAULT_INSTRUCTION)
    }
}
