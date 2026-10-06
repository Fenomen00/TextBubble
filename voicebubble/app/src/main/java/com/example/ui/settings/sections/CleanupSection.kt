package com.example.ui.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiProvider
import com.example.network.TestResult
import com.example.ui.settings.CleanupUiState
import com.example.ui.settings.components.ApiKeyField
import com.example.ui.settings.components.ModelDropdown
import com.example.ui.settings.components.ProviderDropdown
import com.example.ui.settings.components.TestResultCard
import com.example.ui.theme.BorderStroke
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VioletSecondary
import com.example.ui.theme.VioletSecondaryContainer

@Composable
fun CleanupSection(
    state: CleanupUiState,
    transcriptionApiKey: String,
    onToggleEnabled: (Boolean) -> Unit,
    onProviderChange: (AiProvider) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onModelSelect: (String) -> Unit,
    onInstructionChange: (String) -> Unit,
    onResetInstruction: () -> Unit,
    onRefreshModels: () -> Unit,
    onCopyKeyFromTranscription: () -> Unit,
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
            // Header with On/Off Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = VioletSecondaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Text Cleanup",
                            tint = VioletSecondary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "2. AI Cleanup",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = if (state.isEnabled) "Polishes transcripts using chat LLM" else "Off (Raw transcript only)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isEnabled) TextSecondary else TextTertiary
                        )
                    }
                }

                Switch(
                    checked = state.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = EmeraldSuccess,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = BorderStroke
                    ),
                    modifier = Modifier.testTag("cleanup_toggle_switch")
                )
            }

            // If cleanup is disabled, show explanatory info card
            AnimatedVisibility(
                visible = !state.isEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cleanup is disabled. Speech transcripts will be inserted directly into target apps without grammar modifications or filler removal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // If cleanup is enabled, show full controls
            AnimatedVisibility(
                visible = state.isEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 18.dp)) {
                    // Provider Dropdown
                    ProviderDropdown(
                        selectedProvider = state.provider,
                        onProviderSelected = onProviderChange,
                        testTag = "cleanup_provider_dropdown"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick action to copy key from transcription if empty
                    if (state.apiKey.isBlank() && transcriptionApiKey.isNotBlank()) {
                        OutlinedButton(
                            onClick = onCopyKeyFromTranscription,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cleanup_copy_transcription_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Use Same Groq Key as Transcription",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = CyanPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // API Key Field (Own key)
                    ApiKeyField(
                        apiKey = state.apiKey,
                        onApiKeyChange = onApiKeyChange,
                        isApiKeyVisible = state.isApiKeyVisible,
                        onToggleVisibility = onToggleKeyVisibility,
                        label = "Cleanup API Key",
                        placeholder = "Enter Groq API Key (gsk_...)",
                        testTag = "cleanup_api_key_field"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Model Dropdown (Text/chat models only)
                    ModelDropdown(
                        selectedModel = state.selectedModel,
                        availableModels = state.availableModels,
                        isLoading = state.isLoadingModels,
                        onModelSelected = onModelSelect,
                        onRefreshModels = onRefreshModels,
                        statusMessage = state.modelsFetchMessage,
                        label = "Cleanup Model",
                        categoryBadge = "Chat / Text",
                        testTag = "cleanup_model_dropdown"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Cleanup Instruction Text Box
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cleanup Instruction (System Prompt)",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )

                        TextButton(
                            onClick = onResetInstruction,
                            modifier = Modifier.testTag("cleanup_reset_instruction_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                tint = VioletSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reset default",
                                style = MaterialTheme.typography.labelSmall,
                                color = VioletSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = state.instruction,
                        onValueChange = onInstructionChange,
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VioletSecondary,
                            unfocusedBorderColor = BorderStroke,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cleanup_instruction_field")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Test Button
                    Button(
                        onClick = onTestClick,
                        enabled = state.testResult !is TestResult.Testing,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VioletSecondary,
                            contentColor = DarkSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("cleanup_test_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.testResult is TestResult.Testing) "Running Test Cleanup..." else "Test Cleanup Model & Prompt",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Test Result Feedback
                    if (state.testResult !is TestResult.Idle) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TestResultCard(
                            testResult = state.testResult,
                            testTag = "cleanup_test_result_card"
                        )
                    }
                }
            }
        }
    }
}
