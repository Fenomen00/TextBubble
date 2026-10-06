package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.TranscriptRepository
import com.example.model.AiProvider
import com.example.model.CleanupConfig
import com.example.model.TranscriptionConfig
import com.example.network.GroqApiClient
import com.example.overlay.BubbleState
import com.example.overlay.FloatingBubbleView
import com.example.service.AccessibilityDiagnostics
import com.example.ui.history.HistoryViewModel
import com.example.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Voice Bubble Settings", appName)
    }

    @Test
    fun testGroqModelFiltering() {
        val client = GroqApiClient()
        val sampleModels = listOf(
            "whisper-large-v3",
            "whisper-large-v3-turbo",
            "distil-whisper-large-v3-en",
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768",
            "gemma2-9b-it"
        )

        val speechModels = client.filterTranscriptionModels(sampleModels)
        assertEquals(3, speechModels.size)
        assertTrue(speechModels.contains("whisper-large-v3-turbo"))
        assertFalse(speechModels.contains("llama-3.3-70b-versatile"))

        val textModels = client.filterCleanupModels(sampleModels)
        assertEquals(4, textModels.size)
        assertTrue(textModels.contains("llama-3.3-70b-versatile"))
        assertFalse(textModels.contains("whisper-large-v3"))
    }

    @Test
    fun testDefaultConfigurations() {
        assertEquals("whisper-large-v3-turbo", TranscriptionConfig.DEFAULT_TRANSCRIPTION_MODEL)
        assertEquals("llama-3.3-70b-versatile", CleanupConfig.DEFAULT_CLEANUP_MODEL)
        assertTrue(CleanupConfig.DEFAULT_INSTRUCTION.contains("Fix grammar and remove filler words"))
        assertEquals(AiProvider.GROQ, AiProvider.fromId("groq"))
    }

    @Test
    fun testViewModelCreation() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = SettingsViewModel(application)
        assertNotNull(viewModel)
        assertNotNull(viewModel.uiState.value)

        val factory = SettingsViewModel.provideFactory(application)
        val vmFromFactory = factory.create(SettingsViewModel::class.java)
        assertNotNull(vmFromFactory)
    }

    @Test
    fun testRoomDatabaseAndHistory() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getInstance(application)
        val repo = TranscriptRepository(db.transcriptDao())

        repo.clearAll()
        val id = repo.insert("raw hello", "Hello!", wasCleaned = true)
        assertTrue(id > 0)

        val list = repo.allTranscripts.first()
        assertTrue(list.isNotEmpty())
        assertEquals("raw hello", list.first().rawText)
        assertEquals("Hello!", list.first().cleanedText)

        val historyVm = HistoryViewModel(application)
        assertNotNull(historyVm)
    }

    @Test
    fun testAccessibilityDiagnostics() {
        AccessibilityDiagnostics.setServiceConnected(true)
        assertTrue(AccessibilityDiagnostics.isServiceConnected.value)

        AccessibilityDiagnostics.setBubbleAttached(true)
        assertTrue(AccessibilityDiagnostics.isBubbleAttached.value)

        AccessibilityDiagnostics.setAlwaysShowBubble(true)
        assertTrue(AccessibilityDiagnostics.alwaysShowBubble.value)

        AccessibilityDiagnostics.setLastEvent("TYPE_VIEW_FOCUSED")
        assertEquals("TYPE_VIEW_FOCUSED", AccessibilityDiagnostics.lastEventType.value)
        assertTrue(AccessibilityDiagnostics.lastEventTimestamp.value > 0)

        AccessibilityDiagnostics.setLastError("Test error")
        assertEquals("Test error", AccessibilityDiagnostics.lastErrorMessage.value)

        AccessibilityDiagnostics.setServiceConnected(false)
        assertFalse(AccessibilityDiagnostics.isServiceConnected.value)
        assertFalse(AccessibilityDiagnostics.isBubbleAttached.value)
    }

    @Test
    fun testFloatingBubbleViewCreation() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val bubbleView = FloatingBubbleView(
            context = application,
            onRecordStart = {},
            onRecordEnd = {},
            onRetry = {}
        )
        assertNotNull(bubbleView)

        // Verify state transitions without null pointer exceptions
        bubbleView.setState(BubbleState.RECORDING)
        bubbleView.setState(BubbleState.PROCESSING)
        bubbleView.setState(BubbleState.SUCCESS)
        bubbleView.setState(BubbleState.ERROR)
        bubbleView.setState(BubbleState.IDLE)
    }

    @Test
    fun testFloatingBubbleManagerPositioning() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val manager = com.example.overlay.FloatingBubbleManager(
            context = application,
            onRecordStart = {},
            onRecordEnd = {},
            onRetry = {}
        )
        assertNotNull(manager)
        manager.hideBubble()
        assertFalse(manager.isAttached())
    }
}
