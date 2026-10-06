package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.audio.AudioRecorder
import com.example.data.VoiceBubbleSettingsManager
import com.example.data.db.AppDatabase
import com.example.data.db.TranscriptRepository
import com.example.network.GroqApiClient
import com.example.overlay.BubbleState
import com.example.overlay.FloatingBubbleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Accessibility service managing floating voice dictation bubble.
 *
 * Privacy Guarantees:
 * - NEVER reads, logs, or stores text from other apps or fields.
 * - Only detects window types (TYPE_INPUT_METHOD) and issues ACTION_PASTE.
 * - Audio and transcripts are processed locally and only transmitted to configured AI providers.
 */
class VoiceBubbleAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceBubbleService"

        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var bubbleManager: FloatingBubbleManager? = null
    private var audioRecorder: AudioRecorder? = null
    private var recordedAudioFile: File? = null

    private lateinit var settingsManager: VoiceBubbleSettingsManager
    private lateinit var transcriptRepository: TranscriptRepository
    private val groqApiClient = GroqApiClient()

    private var processingJob: Job? = null
    private var debounceJob: Job? = null
    private var isProcessing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        AccessibilityDiagnostics.setServiceConnected(true)
        Log.d(TAG, "VoiceBubbleAccessibilityService connected")

        settingsManager = VoiceBubbleSettingsManager.getInstance(this)
        transcriptRepository = TranscriptRepository(AppDatabase.getInstance(this).transcriptDao())
        audioRecorder = AudioRecorder(this)
        recordedAudioFile = File(cacheDir, "voice_bubble_temp.m4a")

        bubbleManager = FloatingBubbleManager(
            context = this,
            onRecordStart = { startRecording() },
            onRecordEnd = { stopRecordingAndProcess() },
            onRetry = { retryProcessing() }
        )

        // Observe "Always Show Bubble" debug toggle
        serviceScope.launch {
            AccessibilityDiagnostics.alwaysShowBubble.collect { _ ->
                evaluateBubbleVisibility()
            }
        }

        // Initial check for visibility on connection
        evaluateBubbleVisibility()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventTypeName = try {
            AccessibilityEvent.eventTypeToString(event.eventType)
        } catch (_: Exception) {
            "Event_${event.eventType}"
        }
        AccessibilityDiagnostics.setLastEvent(eventTypeName)

        val eventType = event.eventType

        // Instant evaluation when window hierarchy changes (keyboard opens / closes)
        if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            debounceJob?.cancel()
            evaluateBubbleVisibility()
            return
        }

        // Fast debounced evaluation on focus, clicks, or content updates
        val isRelevant = eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED

        if (isRelevant) {
            scheduleEvaluation()
        }
    }

    private fun scheduleEvaluation() {
        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            delay(20)
            evaluateBubbleVisibility()
        }
    }

    /**
     * Checks if a soft keyboard (TYPE_INPUT_METHOD) is currently visible on screen.
     */
    private fun isKeyboardVisible(): Boolean {
        try {
            val windowList = windows ?: return false
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels

            for (window in windowList) {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    val bounds = Rect()
                    window.getBoundsInScreen(bounds)
                    // The keyboard is truly visible if it has height > 100px, width > 0,
                    // and its top edge is strictly inside the visible screen (top < screenHeight)
                    if (bounds.height() > 100 && bounds.width() > 0 && bounds.top < screenHeight && bounds.bottom > 0) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking keyboard visibility: ${e.message}")
        }
        return false
    }

    /**
     * Evaluates whether the floating bubble should be shown or hidden.
     * Positioned higher on the right side of the middle of the screen.
     * It appears smoothly fast from the right edge when the keyboard appears,
     * and disappears as soon as exiting the keyboard.
     * It does NOT move with keyboard height.
     */
    private fun evaluateBubbleVisibility() {
        val isRecording = audioRecorder?.isCurrentlyRecording() == true
        val alwaysShow = AccessibilityDiagnostics.alwaysShowBubble.value
        val keyboardVisible = isKeyboardVisible()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        // Bubble width ~ 56dp
        val bubbleWidthPx = (56 * density).toInt()
        val rightMarginPx = (16 * density).toInt()

        // 1. Right side of the screen
        val targetX = (screenWidth - bubbleWidthPx - rightMarginPx).coerceAtLeast(0)

        // 2. Higher, on the right side of the middle of the screen
        // Positioned at 40% of the screen height (higher than middle 50%),
        // so it never overlaps the keyboard and never moves with it.
        val targetY = ((screenHeight * 0.40f) - (28 * density)).toInt().coerceAtLeast(0)

        if (keyboardVisible) {
            // Keyboard is active: show the bubble fully at this stable location
            bubbleManager?.moveBubbleTo(targetX, targetY)
        } else {
            // Keyboard is not active
            if (alwaysShow) {
                // Keep visible at the same stable position if debug override is enabled
                bubbleManager?.moveBubbleTo(targetX, targetY)
            } else if (!isRecording && !isProcessing) {
                // Hide immediately as soon as the keyboard exits
                bubbleManager?.hideBubble()
            }
        }
    }

    private fun startRecording() {
        val file = recordedAudioFile ?: return
        val started = audioRecorder?.start(file) == true
        if (!started) {
            bubbleManager?.setBubbleState(BubbleState.ERROR, "Microphone error")
        }
    }

    private fun stopRecordingAndProcess() {
        val stopped = audioRecorder?.stop() == true
        val file = recordedAudioFile

        if (!stopped || file == null || !file.exists() || file.length() < 1000) {
            bubbleManager?.setBubbleState(BubbleState.IDLE)
            evaluateBubbleVisibility()
            return
        }

        processAudio(file)
    }

    private fun retryProcessing() {
        val file = recordedAudioFile
        if (file != null && file.exists() && file.length() > 0) {
            bubbleManager?.setBubbleState(BubbleState.PROCESSING, "Retrying...")
            processAudio(file)
        } else {
            bubbleManager?.setBubbleState(BubbleState.IDLE)
            evaluateBubbleVisibility()
        }
    }

    private fun processAudio(audioFile: File) {
        processingJob?.cancel()
        processingJob = serviceScope.launch {
            isProcessing = true
            try {
                val config = settingsManager.getConfig()
                val transKey = config.transcription.apiKey
                val transModel = config.transcription.selectedModel

                if (transKey.isBlank()) {
                    bubbleManager?.setBubbleState(BubbleState.ERROR, "Set Groq key in settings")
                    return@launch
                }

                // Step 1: Transcribe Audio
                bubbleManager?.setBubbleState(BubbleState.PROCESSING, "Transcribing speech...")
                val transcriptionResult = groqApiClient.transcribeAudio(
                    apiKey = transKey,
                    model = transModel,
                    audioFile = audioFile
                )

                if (transcriptionResult.isFailure) {
                    val err = transcriptionResult.exceptionOrNull()?.message ?: "Transcription failed"
                    bubbleManager?.setBubbleState(BubbleState.ERROR, err.take(28))
                    return@launch
                }

                val rawTranscript = transcriptionResult.getOrThrow()
                if (rawTranscript.isBlank()) {
                    bubbleManager?.setBubbleState(BubbleState.ERROR, "No speech detected")
                    return@launch
                }

                // Step 2: AI Cleanup (if enabled)
                val finalText: String
                val wasCleaned: Boolean

                if (config.cleanup.isEnabled && config.cleanup.apiKey.isNotBlank()) {
                    bubbleManager?.setBubbleState(BubbleState.PROCESSING, "Polishing text...")
                    val cleanupResult = groqApiClient.cleanupText(
                        apiKey = config.cleanup.apiKey,
                        model = config.cleanup.selectedModel,
                        instruction = config.cleanup.instruction,
                        rawText = rawTranscript
                    )
                    finalText = cleanupResult.getOrDefault(rawTranscript)
                    wasCleaned = true
                } else {
                    finalText = rawTranscript
                    wasCleaned = false
                }

                // Step 3: Put on clipboard, trigger paste into focused field, restore previous clip
                pasteAndRestoreClipboard(finalText)

                // Step 4: Save to local Room database
                withContext(Dispatchers.IO) {
                    transcriptRepository.insert(
                        rawText = rawTranscript,
                        cleanedText = finalText,
                        wasCleaned = wasCleaned
                    )
                }

                bubbleManager?.setBubbleState(BubbleState.SUCCESS, "Pasted successfully")
            } finally {
                isProcessing = false
                // Re-evaluate visibility after operation completes
                evaluateBubbleVisibility()
            }
        }
    }

    /**
     * Finds the currently focused input field dynamically at paste time and triggers ACTION_PASTE.
     * Never reads or inspects text content from other applications.
     */
    private suspend fun pasteAndRestoreClipboard(textToPaste: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val previousClip: ClipData? = clipboard.primaryClip

        // Put final text on clipboard
        val newClip = ClipData.newPlainText("Voice Bubble", textToPaste)
        clipboard.setPrimaryClip(newClip)

        // Dynamically find currently focused input field
        val focusedInput = try {
            rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } catch (_: Exception) {
            null
        }

        val targetNode = focusedInput ?: try {
            rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        } catch (_: Exception) {
            null
        }

        val pasted = targetNode?.performAction(AccessibilityNodeInfo.ACTION_PASTE) ?: false
        Log.d(TAG, "Pasted into active input field: $pasted")

        // Wait briefly for target application to consume paste event
        delay(600)

        // Restore original clipboard contents
        try {
            if (previousClip != null) {
                clipboard.setPrimaryClip(previousClip)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            }
        } catch (_: Exception) {
            // Ignore clipboard restore issues
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "VoiceBubbleAccessibilityService interrupted")
        if (!AccessibilityDiagnostics.alwaysShowBubble.value && audioRecorder?.isCurrentlyRecording() != true && !isProcessing) {
            bubbleManager?.hideBubble()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        AccessibilityDiagnostics.setServiceConnected(false)
        bubbleManager?.hideBubble()
        Log.d(TAG, "VoiceBubbleAccessibilityService unbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        AccessibilityDiagnostics.setServiceConnected(false)
        bubbleManager?.hideBubble()
        audioRecorder?.stop()
        serviceScope.cancel()
        Log.d(TAG, "VoiceBubbleAccessibilityService destroyed")
    }
}
