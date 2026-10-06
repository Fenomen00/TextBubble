package com.example.ui.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.service.AccessibilityDiagnostics
import com.example.service.VoiceBubbleAccessibilityService
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderStroke
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.CyanPrimaryContainer
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessContainer
import com.example.ui.theme.RoseError
import com.example.ui.theme.RoseErrorContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VioletSecondary
import com.example.ui.theme.VioletSecondaryContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasMicPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var isAccessibilityEnabledBySettings by remember { mutableStateOf(false) }
    var testInputText by remember { mutableStateOf("") }

    // Live diagnostics telemetry
    val isServiceConnected by AccessibilityDiagnostics.isServiceConnected.collectAsState()
    val isBubbleAttached by AccessibilityDiagnostics.isBubbleAttached.collectAsState()
    val lastEventType by AccessibilityDiagnostics.lastEventType.collectAsState()
    val lastEventTimestamp by AccessibilityDiagnostics.lastEventTimestamp.collectAsState()
    val lastErrorMessage by AccessibilityDiagnostics.lastErrorMessage.collectAsState()
    val alwaysShowBubble by AccessibilityDiagnostics.alwaysShowBubble.collectAsState()

    // Refresh permissions on resume
    fun refreshPermissions() {
        hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        hasOverlayPermission = Settings.canDrawOverlays(context)

        isAccessibilityEnabledBySettings = checkAccessibilityServiceEnabled(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshPermissions()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    val totalGranted = listOf(hasMicPermission, hasOverlayPermission, isServiceConnected || isAccessibilityEnabledBySettings).count { it }
    val allGranted = totalGranted == 3

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Voice Bubble Setup",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (allGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (allGranted) EmeraldSuccess else AmberWarning,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (allGranted) "3/3 Ready • Bubble Active" else "$totalGranted/3 Permissions Configured",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = if (allGranted) EmeraldSuccess else AmberWarning
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                ),
                modifier = Modifier.testTag("setup_top_app_bar")
            )
        },
        containerColor = DarkBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // 1. Live Diagnostic Status Panel (Requested Feature)
                LiveDiagnosticStatusPanel(
                    isServiceConnected = isServiceConnected,
                    hasOverlayPermission = hasOverlayPermission,
                    isBubbleAttached = isBubbleAttached,
                    lastEventType = lastEventType,
                    lastEventTimestamp = lastEventTimestamp,
                    lastErrorMessage = lastErrorMessage,
                    alwaysShowBubble = alwaysShowBubble,
                    onToggleAlwaysShowBubble = { AccessibilityDiagnostics.setAlwaysShowBubble(it) }
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 2. Permission Items
                Text(
                    text = "System Permissions Setup",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // 1. Microphone Permission
                PermissionItemCard(
                    title = "1. Microphone Access",
                    description = "Needed to record speech when you press and hold the floating bubble.",
                    isGranted = hasMicPermission,
                    icon = Icons.Default.Mic,
                    iconBg = CyanPrimaryContainer,
                    iconTint = CyanPrimary,
                    actionText = "Grant Microphone Permission",
                    onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    testTag = "perm_mic"
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Overlay Permission
                PermissionItemCard(
                    title = "2. Display Over Other Apps",
                    description = "Allows overlay windows over other applications.",
                    isGranted = hasOverlayPermission,
                    icon = Icons.Default.Layers,
                    iconBg = VioletSecondaryContainer,
                    iconTint = VioletSecondary,
                    actionText = "Open Overlay Settings",
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    testTag = "perm_overlay"
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Accessibility Service
                PermissionItemCard(
                    title = "3. Accessibility Service",
                    description = "Watches strictly for when a text field is focused to show the bubble, and pastes polished speech. (Uses TYPE_ACCESSIBILITY_OVERLAY).",
                    isGranted = isServiceConnected || isAccessibilityEnabledBySettings,
                    icon = Icons.Default.Accessibility,
                    iconBg = EmeraldSuccessContainer,
                    iconTint = EmeraldSuccess,
                    actionText = "Open Accessibility Settings",
                    onAction = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    testTag = "perm_accessibility"
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 3. In-App Testing Section
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderStroke, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Try It Here (Interactive Test Field)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Tap into the input box below. If the service is running, the floating microphone bubble appears at the bottom! Press and hold to dictate, then release to transcribe and paste.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = testInputText,
                            onValueChange = { testInputText = it },
                            placeholder = {
                                Text("Focus here to test floating microphone bubble...", color = TextTertiary)
                            },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = BorderStroke,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_test_text_field")
                        )

                        if (testInputText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { testInputText = "" },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Clear text", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Configure API keys banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderStroke.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Configure AI Models & Keys",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Choose Whisper models and cleanup instructions in Settings.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        Button(
                            onClick = onNavigateToSettings,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPrimary,
                                contentColor = DarkBackground
                            )
                        ) {
                            Text("Settings", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Live diagnostic telemetry status panel.
 */
@Composable
private fun LiveDiagnosticStatusPanel(
    isServiceConnected: Boolean,
    hasOverlayPermission: Boolean,
    isBubbleAttached: Boolean,
    lastEventType: String,
    lastEventTimestamp: Long,
    lastErrorMessage: String?,
    alwaysShowBubble: Boolean,
    onToggleAlwaysShowBubble: (Boolean) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm:ss a", Locale.getDefault()) }
    val formattedEventTime = remember(lastEventTimestamp) {
        if (lastEventTimestamp > 0) timeFormat.format(Date(lastEventTimestamp)) else "Never"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .testTag("status_panel")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live Status & Diagnostics",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isServiceConnected) EmeraldSuccessContainer else RoseErrorContainer
                ) {
                    Text(
                        text = if (isServiceConnected) "Service Online" else "Service Offline",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isServiceConnected) EmeraldSuccess else RoseError,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Debug Toggle 1: "Always show bubble"
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = if (alwaysShowBubble) CyanPrimary else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Always show bubble (Debug)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Shows the bubble immediately and keeps it visible, ignoring focus events.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = alwaysShowBubble,
                        onCheckedChange = onToggleAlwaysShowBubble,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = CyanPrimary,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = BorderStroke
                        ),
                        modifier = Modifier.testTag("debug_always_show_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live status items
            StatusMetricRow(
                label = "Accessibility service connected",
                valueText = if (isServiceConnected) "Yes" else "No",
                isPositive = isServiceConnected,
                testTag = "status_service_connected"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = BorderStroke.copy(alpha = 0.5f)
            )

            StatusMetricRow(
                label = "Overlay permission granted",
                valueText = if (hasOverlayPermission) "Yes" else "No",
                isPositive = hasOverlayPermission,
                testTag = "status_overlay_permission"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = BorderStroke.copy(alpha = 0.5f)
            )

            StatusMetricRow(
                label = "Bubble attached to WindowManager",
                valueText = if (isBubbleAttached) "Yes (Visible)" else "No",
                isPositive = isBubbleAttached,
                testTag = "status_bubble_attached"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = BorderStroke.copy(alpha = 0.5f)
            )

            // Last Event
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Last accessibility event received",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = lastEventType,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = CyanPrimary,
                        modifier = Modifier.testTag("status_last_event_type")
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DarkSurfaceContainer
                ) {
                    Text(
                        text = formattedEventTime,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextTertiary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Last error (if any)
            if (!lastErrorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = RoseErrorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = RoseError,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Last Overlay Error:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = RoseError
                            )
                            Text(
                                text = lastErrorMessage,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = TextPrimary,
                                modifier = Modifier.testTag("status_last_error_message")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMetricRow(
    label: String,
    valueText: String,
    isPositive: Boolean,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isPositive) EmeraldSuccessContainer else DarkSurfaceVariant,
            modifier = Modifier.testTag(testTag)
        ) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isPositive) EmeraldSuccess else TextTertiary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    iconBg: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    actionText: String,
    onAction: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isGranted) EmeraldSuccess.copy(alpha = 0.4f) else BorderStroke,
                RoundedCornerShape(14.dp)
            )
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconBg,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isGranted) EmeraldSuccessContainer else AmberWarning.copy(alpha = 0.2f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isGranted) EmeraldSuccess else AmberWarning,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isGranted) "Granted" else "Action Required",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = if (isGranted) EmeraldSuccess else AmberWarning,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = DarkBackground
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("${testTag}_action")
                ) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun checkAccessibilityServiceEnabled(context: Context): Boolean {
    return try {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        enabledServices.contains(context.packageName)
    } catch (_: Exception) {
        false
    }
}
