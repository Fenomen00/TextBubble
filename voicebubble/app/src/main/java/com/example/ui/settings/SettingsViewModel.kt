package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.VoiceBubbleSettingsManager
import com.example.data.VoiceBubbleSettingsRepository
import com.example.model.AiProvider
import com.example.model.CleanupConfig
import com.example.model.TranscriptionConfig
import com.example.network.GroqApiClient
import com.example.network.TestResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SectionUiState(
    val provider: AiProvider = AiProvider.GROQ,
    val apiKey: String = "",
    val isApiKeyVisible: Boolean = false,
    val selectedModel: String = "",
    val availableModels: List<String> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsFetchMessage: String? = null,
    val testResult: TestResult = TestResult.Idle
)

data class CleanupUiState(
    val isEnabled: Boolean = true,
    val provider: AiProvider = AiProvider.GROQ,
    val apiKey: String = "",
    val isApiKeyVisible: Boolean = false,
    val selectedModel: String = "",
    val availableModels: List<String> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsFetchMessage: String? = null,
    val instruction: String = CleanupConfig.DEFAULT_INSTRUCTION,
    val testResult: TestResult = TestResult.Idle
)

data class SettingsScreenState(
    val transcription: SectionUiState = SectionUiState(
        selectedModel = TranscriptionConfig.DEFAULT_TRANSCRIPTION_MODEL,
        availableModels = TranscriptionConfig.DEFAULT_SPEECH_MODELS
    ),
    val cleanup: CleanupUiState = CleanupUiState(
        selectedModel = CleanupConfig.DEFAULT_CLEANUP_MODEL,
        availableModels = CleanupConfig.DEFAULT_TEXT_MODELS
    ),
    val toastMessage: String? = null
)

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: VoiceBubbleSettingsRepository =
        VoiceBubbleSettingsManager.getInstance(application).getRepository(),
    private val groqApiClient: GroqApiClient = GroqApiClient()
) : AndroidViewModel(application) {

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(application) as T
                }
            }
    }

    private val _uiState = MutableStateFlow(SettingsScreenState())
    val uiState: StateFlow<SettingsScreenState> = _uiState.asStateFlow()

    private var transcriptionModelsJob: Job? = null
    private var cleanupModelsJob: Job? = null

    init {
        try {
            // Load initial state from persistent repository
            val config = repository.getConfig()
            _uiState.update { current ->
                current.copy(
                    transcription = current.transcription.copy(
                        provider = config.transcription.provider,
                        apiKey = config.transcription.apiKey,
                        selectedModel = config.transcription.selectedModel
                    ),
                    cleanup = current.cleanup.copy(
                        isEnabled = config.cleanup.isEnabled,
                        provider = config.cleanup.provider,
                        apiKey = config.cleanup.apiKey,
                        selectedModel = config.cleanup.selectedModel,
                        instruction = config.cleanup.instruction
                    )
                )
            }

            // If keys are already stored, fetch fresh models list in background
            if (config.transcription.apiKey.isNotBlank() && config.transcription.provider == AiProvider.GROQ) {
                fetchTranscriptionModels(silent = true)
            }
            if (config.cleanup.apiKey.isNotBlank() && config.cleanup.provider == AiProvider.GROQ) {
                fetchCleanupModels(silent = true)
            }
        } catch (_: Exception) {
            // Keep clean default initial state if anything fails
        }
    }

    // ==========================================
    // Transcription Actions
    // ==========================================

    fun onTranscriptionProviderChange(provider: AiProvider) {
        _uiState.update { it.copy(transcription = it.transcription.copy(provider = provider)) }
        viewModelScope.launch { repository.updateTranscriptionConfig(provider = provider) }
    }

    fun onTranscriptionApiKeyChange(key: String) {
        _uiState.update {
            it.copy(
                transcription = it.transcription.copy(
                    apiKey = key,
                    testResult = TestResult.Idle
                )
            )
        }
        viewModelScope.launch { repository.updateTranscriptionConfig(apiKey = key) }
    }

    fun toggleTranscriptionKeyVisibility() {
        _uiState.update {
            it.copy(
                transcription = it.transcription.copy(
                    isApiKeyVisible = !it.transcription.isApiKeyVisible
                )
            )
        }
    }

    fun onTranscriptionModelSelect(model: String) {
        _uiState.update {
            it.copy(transcription = it.transcription.copy(selectedModel = model))
        }
        viewModelScope.launch { repository.updateTranscriptionConfig(model = model) }
    }

    fun fetchTranscriptionModels(silent: Boolean = false) {
        val apiKey = _uiState.value.transcription.apiKey
        if (apiKey.isBlank()) {
            if (!silent) {
                _uiState.update {
                    it.copy(
                        transcription = it.transcription.copy(
                            modelsFetchMessage = "Enter an API key to load live models from Groq"
                        )
                    )
                }
            }
            return
        }

        transcriptionModelsJob?.cancel()
        transcriptionModelsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    transcription = it.transcription.copy(
                        isLoadingModels = true,
                        modelsFetchMessage = null
                    )
                )
            }

            val result = groqApiClient.fetchModels(apiKey)
            result.onSuccess { allModels ->
                val speechModels = groqApiClient.filterTranscriptionModels(allModels)
                val currentSelected = _uiState.value.transcription.selectedModel
                val updatedSelected = if (speechModels.contains(currentSelected)) {
                    currentSelected
                } else {
                    speechModels.firstOrNull() ?: TranscriptionConfig.DEFAULT_TRANSCRIPTION_MODEL
                }

                _uiState.update {
                    it.copy(
                        transcription = it.transcription.copy(
                            availableModels = speechModels,
                            selectedModel = updatedSelected,
                            isLoadingModels = false,
                            modelsFetchMessage = "Loaded ${speechModels.size} speech models from Groq"
                        )
                    )
                }
                repository.updateTranscriptionConfig(model = updatedSelected)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        transcription = it.transcription.copy(
                            isLoadingModels = false,
                            modelsFetchMessage = if (silent) null else "Could not load models: ${err.message}"
                        )
                    )
                }
            }
        }
    }

    fun testTranscription() {
        val apiKey = _uiState.value.transcription.apiKey
        val model = _uiState.value.transcription.selectedModel

        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    transcription = it.transcription.copy(
                        testResult = TestResult.Error("API key cannot be empty. Please enter your Groq key.")
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    transcription = it.transcription.copy(
                        testResult = TestResult.Testing
                    )
                )
            }

            val result = groqApiClient.testTranscriptionKey(apiKey, model)
            _uiState.update {
                it.copy(transcription = it.transcription.copy(testResult = result))
            }

            // Also refresh models on successful test
            if (result is TestResult.Success) {
                fetchTranscriptionModels(silent = true)
            }
        }
    }

    // ==========================================
    // Cleanup Actions
    // ==========================================

    fun onCleanupToggle(enabled: Boolean) {
        _uiState.update {
            it.copy(cleanup = it.cleanup.copy(isEnabled = enabled))
        }
        viewModelScope.launch { repository.updateCleanupConfig(isEnabled = enabled) }
    }

    fun onCleanupProviderChange(provider: AiProvider) {
        _uiState.update { it.copy(cleanup = it.cleanup.copy(provider = provider)) }
        viewModelScope.launch { repository.updateCleanupConfig(provider = provider) }
    }

    fun onCleanupApiKeyChange(key: String) {
        _uiState.update {
            it.copy(
                cleanup = it.cleanup.copy(
                    apiKey = key,
                    testResult = TestResult.Idle
                )
            )
        }
        viewModelScope.launch { repository.updateCleanupConfig(apiKey = key) }
    }

    fun toggleCleanupKeyVisibility() {
        _uiState.update {
            it.copy(
                cleanup = it.cleanup.copy(
                    isApiKeyVisible = !it.cleanup.isApiKeyVisible
                )
            )
        }
    }

    fun onCleanupModelSelect(model: String) {
        _uiState.update {
            it.copy(cleanup = it.cleanup.copy(selectedModel = model))
        }
        viewModelScope.launch { repository.updateCleanupConfig(model = model) }
    }

    fun onCleanupInstructionChange(instruction: String) {
        _uiState.update {
            it.copy(cleanup = it.cleanup.copy(instruction = instruction))
        }
        viewModelScope.launch { repository.updateCleanupConfig(instruction = instruction) }
    }

    fun resetCleanupInstruction() {
        val defaultInstruction = CleanupConfig.DEFAULT_INSTRUCTION
        _uiState.update {
            it.copy(cleanup = it.cleanup.copy(instruction = defaultInstruction))
        }
        viewModelScope.launch { repository.resetCleanupInstruction() }
    }

    fun copyTranscriptionKeyToCleanup() {
        val transKey = _uiState.value.transcription.apiKey
        if (transKey.isNotBlank()) {
            onCleanupApiKeyChange(transKey)
            fetchCleanupModels(silent = false)
        }
    }

    fun fetchCleanupModels(silent: Boolean = false) {
        val apiKey = _uiState.value.cleanup.apiKey
        if (apiKey.isBlank()) {
            if (!silent) {
                _uiState.update {
                    it.copy(
                        cleanup = it.cleanup.copy(
                            modelsFetchMessage = "Enter an API key to load live models from Groq"
                        )
                    )
                }
            }
            return
        }

        cleanupModelsJob?.cancel()
        cleanupModelsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cleanup = it.cleanup.copy(
                        isLoadingModels = true,
                        modelsFetchMessage = null
                    )
                )
            }

            val result = groqApiClient.fetchModels(apiKey)
            result.onSuccess { allModels ->
                val textModels = groqApiClient.filterCleanupModels(allModels)
                val currentSelected = _uiState.value.cleanup.selectedModel
                val updatedSelected = if (textModels.contains(currentSelected)) {
                    currentSelected
                } else {
                    textModels.firstOrNull() ?: CleanupConfig.DEFAULT_CLEANUP_MODEL
                }

                _uiState.update {
                    it.copy(
                        cleanup = it.cleanup.copy(
                            availableModels = textModels,
                            selectedModel = updatedSelected,
                            isLoadingModels = false,
                            modelsFetchMessage = "Loaded ${textModels.size} chat models from Groq"
                        )
                    )
                }
                repository.updateCleanupConfig(model = updatedSelected)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        cleanup = it.cleanup.copy(
                            isLoadingModels = false,
                            modelsFetchMessage = if (silent) null else "Could not load models: ${err.message}"
                        )
                    )
                }
            }
        }
    }

    fun testCleanup() {
        val apiKey = _uiState.value.cleanup.apiKey
        val model = _uiState.value.cleanup.selectedModel
        val instruction = _uiState.value.cleanup.instruction

        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    cleanup = it.cleanup.copy(
                        testResult = TestResult.Error("API key cannot be empty. Please enter your Groq key.")
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cleanup = it.cleanup.copy(
                        testResult = TestResult.Testing
                    )
                )
            }

            val result = groqApiClient.testCleanupKeyAndInstruction(apiKey, model, instruction)
            _uiState.update {
                it.copy(cleanup = it.cleanup.copy(testResult = result))
            }

            // Also refresh models on successful test
            if (result is TestResult.Success) {
                fetchCleanupModels(silent = true)
            }
        }
    }

    fun dismissToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
