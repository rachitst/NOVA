package com.nova.assistant.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.core.model.AssistantState
import com.nova.assistant.ui.enrollment.VoiceEnrollmentDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantDashboardScreen(
    uiState: AssistantUiState,
    onRequestPermissionClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onToggleBackgroundService: () -> Unit,
    onOpenVoiceEnrollment: () -> Unit,
    onStartVoiceEnrollment: (String) -> Unit,
    onRecordEnrollmentSample: () -> Unit,
    onCancelVoiceEnrollment: () -> Unit,
    onDismissVoiceEnrollment: () -> Unit,
    onClearVoiceEnrollment: () -> Unit,
    onDevTtsTestClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (uiState.isEnrollmentDialogOpen) {
        VoiceEnrollmentDialog(
            enrollmentState = uiState.enrollmentState,
            onStartEnrollment = onStartVoiceEnrollment,
            onRecordSample = onRecordEnrollmentSample,
            onCancel = onCancelVoiceEnrollment,
            onDismiss = onDismissVoiceEnrollment
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "NOVA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Offline Hands-Free Voice Assistant",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Permission Banner (if not granted)
            if (!uiState.hasMicrophonePermission) {
                item {
                    PermissionBanner(
                        isPermanentlyDenied = uiState.isPermissionPermanentlyDenied,
                        onRequestPermission = onRequestPermissionClick,
                        onOpenSettings = onOpenSettingsClick
                    )
                }
            }

            // 2. DEV Diagnostic & Live Pipeline Telemetry Overlay Card (Visible in DEV mode)
            if (uiState.devTelemetry.isDevEnabled) {
                item {
                    DevDebugOverlayCard(
                        telemetry = uiState.devTelemetry,
                        onDevTtsTestClick = onDevTtsTestClick
                    )
                }
            }

            // 3. Primary Hands-Free Status Hero Card (Unified Single Mode)
            item {
                HandsFreeHeroCard(
                    isEarphonesConnected = uiState.isEarphonesConnected,
                    isSpeakerEnrolled = uiState.isSpeakerEnrolled,
                    speakerName = uiState.enrolledSpeakerName,
                    isServiceRunning = uiState.isBackgroundServiceRunning,
                    serviceStatus = uiState.serviceStatusMessage,
                    assistantState = uiState.assistantState,
                    hasPermission = uiState.hasMicrophonePermission,
                    onOpenEnrollment = onOpenVoiceEnrollment,
                    onToggleService = onToggleBackgroundService
                )
            }

            // 3. Voice Profile Security Card
            item {
                VoiceProfileCard(
                    isEnrolled = uiState.isSpeakerEnrolled,
                    speakerName = uiState.enrolledSpeakerName,
                    onOpenEnrollment = onOpenVoiceEnrollment,
                    onClearEnrollment = onClearVoiceEnrollment
                )
            }

            // 4. Voice Transcript & Assistant Response Display
            item {
                TranscriptCard(
                    liveTranscript = uiState.liveTranscript,
                    lastTranscript = uiState.lastTranscript,
                    lastResult = uiState.lastResult,
                    assistantState = uiState.assistantState
                )
            }

            // 5. Sample Natural Language Commands Guide
            item {
                SampleCommandsCard()
            }

            // 6. Recent History
            if (uiState.actionHistory.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Session History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${uiState.actionHistory.size} commands",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                items(uiState.actionHistory) { historyItem ->
                    HistoryItemCard(item = historyItem)
                }
            }
        }
    }
}

/**
 * Hero Card representing the unified Hands-Free Mode and its activation states:
 * 1. Active: Earphones connected + Voice profile enrolled.
 * 2. Waiting for Earphones: Earphones not connected.
 * 3. Voice Enrollment Required: Earphones connected, but voice not yet enrolled.
 */
@Composable
fun HandsFreeHeroCard(
    isEarphonesConnected: Boolean,
    isSpeakerEnrolled: Boolean,
    speakerName: String?,
    isServiceRunning: Boolean,
    serviceStatus: String,
    assistantState: AssistantState,
    hasPermission: Boolean,
    onOpenEnrollment: () -> Unit,
    onToggleService: () -> Unit
) {
    val isFullyActive = isEarphonesConnected && isSpeakerEnrolled && isServiceRunning

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by if (isFullyActive) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        rememberInfiniteTransition(label = "idle").animateFloat(
            initialValue = 1.0f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(1000)),
            label = "idleScale"
        )
    }

    val (cardColor, iconText, titleText, descText, badgeText, badgeColor) = when {
        !isEarphonesConnected -> Sextet(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            "🎧",
            "Earphones Disconnected",
            "Connect wired or Bluetooth earphones to activate hands-free listening.",
            "Standby",
            MaterialTheme.colorScheme.outline
        )
        !isSpeakerEnrolled -> Sextet(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            "🎙️",
            "Voice Enrollment Required",
            "Earphones connected! Train NOVA to your voice before hands-free mode activates.",
            "Setup Needed",
            MaterialTheme.colorScheme.tertiary
        )
        else -> Sextet(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            "🟢",
            "Hands-Free Active",
            "Listening for 'Hey NOVA' through connected earphones.",
            "Listening",
            Color(0xFF2E7D32)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = iconText, fontSize = 22.sp)
                    }

                    Column {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }
                }

                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { onToggleService() },
                    enabled = hasPermission
                )
            }

            Text(
                text = descText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Dynamic Action Button depending on state
            if (isEarphonesConnected && !isSpeakerEnrolled) {
                Button(
                    onClick = onOpenEnrollment,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("🔒 Set Up Voice Profile")
                }
            }
        }
    }
}

@Composable
fun VoiceProfileCard(
    isEnrolled: Boolean,
    speakerName: String?,
    onOpenEnrollment: () -> Unit,
    onClearEnrollment: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnrolled) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnrolled) "Voice Profile: $speakerName" else "No Voice Profile",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEnrolled) {
                        "Speaker verification is enforced on all 'Hey NOVA' wake events."
                    } else {
                        "Enroll your voice to enable secure hands-free access."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isEnrolled) {
                OutlinedButton(onClick = onOpenEnrollment) {
                    Text("Retrain")
                }
            } else {
                Button(
                    onClick = onOpenEnrollment,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Enroll")
                }
            }
        }
    }
}

@Composable
fun TranscriptCard(
    liveTranscript: String,
    lastTranscript: String?,
    lastResult: ActionResult?,
    assistantState: AssistantState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Voice Transcript & Spoken Response",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            val displayTranscript = when {
                liveTranscript.isNotBlank() -> liveTranscript
                lastTranscript != null -> lastTranscript
                else -> "Say 'Hey NOVA' through connected earphones to speak a command."
            }

            Text(
                text = displayTranscript,
                style = MaterialTheme.typography.bodyLarge,
                color = if (liveTranscript.isNotBlank() || lastTranscript != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )

            if (lastResult != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (lastResult is ActionResult.Success) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        text = "NOVA: ${lastResult.spokenResponse}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (lastResult is ActionResult.Success) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
fun SampleCommandsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Sample Natural Commands",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Speak naturally with conversational phrasing:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("What time is it?") }
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text("Turn on flashlight") }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("Can you call Mom?") }
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text("Set an alarm for 7:30 AM") }
                )
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: ActionHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "\"${item.transcript}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.result.displayFeedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = if (item.result is ActionResult.Success) "✓" else "✕",
                color = if (item.result is ActionResult.Success) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun PermissionBanner(
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Microphone Permission Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = if (isPermanentlyDenied) {
                    "Microphone access was denied. Please grant permission in Android Settings to enable voice commands."
                } else {
                    "NOVA requires microphone access to hear and process your spoken commands locally."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isPermanentlyDenied) {
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Open Settings")
                    }
                } else {
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}

@Composable
fun DevDebugOverlayCard(
    telemetry: com.nova.assistant.core.diagnostics.DevTelemetryState,
    onDevTtsTestClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF131722)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "⚡", fontSize = 18.sp)
                    Text(
                        text = "DEV PIPELINE TELEMETRY",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = telemetry.activeAudioDevice,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF00E5FF)
                    )
                }
            }

            // DEV: TTS output routing test
            Button(
                onClick = onDevTtsTestClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF).copy(alpha = 0.2f))
            ) {
                Text(text = "DEV: TTS OUTPUT TEST", color = Color(0xFF00E5FF))
            }

            // 1. Audio Energy & Peak Meter
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PCM Live Energy",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA0AAB8)
                    )
                    Text(
                        text = "RMS: ${String.format("%.1f", telemetry.currentRms)} | Peak: ${telemetry.currentPeak}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (telemetry.currentRms > 50f) Color(0xFF00E676) else Color(0xFFA0AAB8)
                    )
                }
                val rmsPercent = (telemetry.currentRms / 400.0f).coerceIn(0.0f, 1.0f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF222838))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(rmsPercent)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (rmsPercent > 0.6f) Color(0xFFFF5252)
                                else if (rmsPercent > 0.2f) Color(0xFF00E676)
                                else Color(0xFF2979FF)
                            )
                    )
                }
            }

            // 2. Wake-Word DTW Alignment Meter
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Wake DTW (${if (telemetry.isWakeCalibrated) "Calibrated" else "Universal"})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA0AAB8)
                    )
                    Text(
                        text = "Score: ${String.format("%.2f", telemetry.lastWakeScore)} / ${String.format("%.2f", telemetry.lastWakeThreshold)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (telemetry.lastWakeScore >= telemetry.lastWakeThreshold) Color(0xFF00E676)
                        else if (telemetry.lastWakeScore >= 0.38f) Color(0xFFFFD600)
                        else Color(0xFFA0AAB8)
                    )
                }
                val wakePercent = (telemetry.lastWakeScore / 1.0f).coerceIn(0.0f, 1.0f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF222838))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(wakePercent)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (telemetry.lastWakeScore >= telemetry.lastWakeThreshold) Color(0xFF00E676)
                                else if (telemetry.lastWakeScore >= 0.38f) Color(0xFFFFD600)
                                else Color(0xFF37474F)
                            )
                    )
                }
            }

            // 3. Speaker Verification Meter
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Speaker Voiceprint (${telemetry.lastSpeakerName ?: "Unenrolled"})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA0AAB8)
                    )
                    Text(
                        text = "Sim: ${String.format("%.2f", telemetry.lastSpeakerScore)} / ${String.format("%.2f", telemetry.lastSpeakerThreshold)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (telemetry.lastSpeakerScore >= telemetry.lastSpeakerThreshold) Color(0xFF00E676) else Color(0xFFFF5252)
                    )
                }
            }

            // 4. Recent Diagnostic Event Stream (Chronological pipeline decisions)
            if (telemetry.recentEvents.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0A0D14))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Decision Engine Log",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF78909C)
                    )

                    telemetry.recentEvents.take(5).forEach { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = event.formattedTime.takeLast(12),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = Color(0xFF546E7A)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (event.isSuccess) Color(0xFF00E676).copy(alpha = 0.2f)
                                        else Color(0xFFFF5252).copy(alpha = 0.2f)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${event.stage}:${event.status}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (event.isSuccess) Color(0xFF00E676) else Color(0xFFFF5252)
                                )
                            }
                            Text(
                                text = event.details,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color(0xFFCFD8DC),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Sextet<A, B, C, D, E, F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
)

