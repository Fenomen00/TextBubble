package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live diagnostic telemetry for the Voice Bubble Accessibility Service & Overlay.
 */
object AccessibilityDiagnostics {
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _isBubbleAttached = MutableStateFlow(false)
    val isBubbleAttached: StateFlow<Boolean> = _isBubbleAttached.asStateFlow()

    private val _lastEventType = MutableStateFlow("None yet")
    val lastEventType: StateFlow<String> = _lastEventType.asStateFlow()

    private val _lastEventTimestamp = MutableStateFlow(0L)
    val lastEventTimestamp: StateFlow<Long> = _lastEventTimestamp.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _alwaysShowBubble = MutableStateFlow(false)
    val alwaysShowBubble: StateFlow<Boolean> = _alwaysShowBubble.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _isServiceConnected.value = connected
        if (!connected) {
            _isBubbleAttached.value = false
        }
    }

    fun setBubbleAttached(attached: Boolean) {
        _isBubbleAttached.value = attached
    }

    fun setLastEvent(eventType: String) {
        _lastEventType.value = eventType
        _lastEventTimestamp.value = System.currentTimeMillis()
    }

    fun setLastError(error: String?) {
        _lastErrorMessage.value = error
    }

    fun setAlwaysShowBubble(always: Boolean) {
        _alwaysShowBubble.value = always
    }
}
