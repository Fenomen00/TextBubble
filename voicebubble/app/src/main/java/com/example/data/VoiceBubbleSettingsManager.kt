package com.example.data

import android.content.Context
import com.example.model.VoiceBubbleConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Single access point for reading and observing Voice Bubble settings across
 * the entire application, including future background services (e.g. FloatingBubbleService,
 * AudioRecordService, AccessibilityService).
 *
 * Example usage in future background service:
 * ```kotlin
 * class FloatingBubbleService : Service() {
 *     override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
 *         // Read current settings synchronously
 *         val config = VoiceBubbleSettingsManager.getInstance(this).getConfig()
 *         val transcriptionKey = config.transcription.apiKey
 *         val isCleanupEnabled = config.cleanup.isEnabled
 *         ...
 *         return START_STICKY
 *     }
 * }
 * ```
 */
class VoiceBubbleSettingsManager private constructor(context: Context) {

    private val repository: VoiceBubbleSettingsRepository =
        VoiceBubbleSettingsRepositoryImpl(context.applicationContext)

    /**
     * Flow of configuration changes, useful for reactive service components.
     */
    val configFlow: StateFlow<VoiceBubbleConfig> = repository.configFlow

    /**
     * Synchronously retrieves the current settings.
     * Safe to call from background threads or services.
     */
    fun getConfig(): VoiceBubbleConfig = repository.getConfig()

    /**
     * Access underlying repository for modifications.
     */
    fun getRepository(): VoiceBubbleSettingsRepository = repository

    companion object {
        @Volatile
        private var instance: VoiceBubbleSettingsManager? = null

        fun getInstance(context: Context): VoiceBubbleSettingsManager {
            return instance ?: synchronized(this) {
                instance ?: VoiceBubbleSettingsManager(context).also { instance = it }
            }
        }
    }
}
