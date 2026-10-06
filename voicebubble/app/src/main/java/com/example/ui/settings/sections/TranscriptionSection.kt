package com.example.ui.settings.sections

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AiProvider
import com.example.network.TestResult
import com.example.ui.settings.SectionUiState
import com.example.ui.settings.components.ApiKeyField
import com.example.ui.settings.components.ModelDropdown
import com.example.ui.settings.components.ProviderDropdown
import com.example.ui.settings.components.TestResultCard
import com.example.ui.theme.BorderStroke
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.CyanPrimaryContainer
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TranscriptionSection(
    state: SectionUiState,
    onProviderChange: (AiProvider) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onModelSelect: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onTestClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderStroke, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
        ) {
            // Section Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = CyanPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Microphone Transcription",
                        tint = CyanPrimary,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "1. Transcription",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Converts spoken audio into text using speech models",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Provider Dropdown
            ProviderDropdown(
                selectedProvider = state.provider,
                onProviderSelected = onProviderChange,
                testTag = "transcription_provider_dropdown"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // API Key Field (Each section has its own key)
            ApiKeyField(
                apiKey = state.apiKey,
                onApiKeyChange = onApiKeyChange,
                isApiKeyVisible = state.isApiKeyVisible,
                onToggleVisibility = onToggleKeyVisibility,
                label = "Transcription API Key",
                placeholder = "Enter Groq API Key (gsk_...)",
                testTag = "transcription_api_key_field"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Model Dropdown (Whisper/speech models only)
            ModelDropdown(
                selectedModel = state.selectedModel,
                availableModels = state.availableModels,
                isLoading = state.isLoadingModels,
                onModelSelected = onModelSelect,
                onRefreshModels = onRefreshModels,
                statusMessage = state.modelsFetchMessage,
                label = "Transcription Model",
                categoryBadge = "Whisper / Speech",
                testTag = "transcription_model_dropdown"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Test Button
            Button(
                onClick = onTestClick,
                enabled = state.testResult !is TestResult.Testing,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = DarkSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("transcription_test_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.testResult is TestResult.Testing) "Testing Connection..." else "Test Transcription Key",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            // Test Result Feedback
            if (state.testResult !is TestResult.Idle) {
                Spacer(modifier = Modifier.height(12.dp))
                TestResultCard(
                    testResult = state.testResult,
                    testTag = "transcription_test_result_card"
                )
            }
        }
    }
}
