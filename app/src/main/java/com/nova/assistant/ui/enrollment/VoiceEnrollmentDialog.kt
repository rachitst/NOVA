package com.nova.assistant.ui.enrollment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nova.assistant.voice.speaker.EnrollmentState

/**
 * Interactive Dialog guiding the user through secure voice enrollment.
 */
/**
 * Interactive Dialog guiding the user through secure voice enrollment.
 */
@Composable
fun VoiceEnrollmentDialog(
    enrollmentState: EnrollmentState,
    onStartEnrollment: (String) -> Unit,
    onRecordSample: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var speakerNameInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = {
        onCancel()
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔒",
                        fontSize = 24.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Secure Voice Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "NOVA requires speaker verification before activating commands. Voice data is processed 100% on-device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                )

                when (enrollmentState) {
                    is EnrollmentState.Idle -> {
                        OutlinedTextField(
                            value = speakerNameInput,
                            onValueChange = { speakerNameInput = it },
                            label = { Text("Your Name / Profile Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(onClick = {
                                onCancel()
                                onDismiss()
                            }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val name = speakerNameInput.trim().ifBlank { "Owner" }
                                    onStartEnrollment(name)
                                }
                            ) {
                                Text("Continue")
                            }
                        }
                    }

                    is EnrollmentState.ReadyForSample -> {
                        Text(
                            text = "Sample ${enrollmentState.currentSampleIndex} of ${enrollmentState.totalSamples}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Say \"Hey NOVA\"",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Tap the button below and speak clearly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(onClick = {
                                onCancel()
                                onDismiss()
                            }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = onRecordSample,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("🎙️ Record Sample ${enrollmentState.currentSampleIndex}")
                            }
                        }
                    }

                    is EnrollmentState.RecordingSample -> {
                        Text(
                            text = "Listening... Say \"Hey NOVA\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Sample ${enrollmentState.currentSampleIndex} of ${enrollmentState.totalSamples}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        LinearProgressIndicator(
                            progress = { enrollmentState.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                    }

                    is EnrollmentState.SampleRetry -> {
                        Text(text = "⚠️", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sample ${enrollmentState.sampleIndex} Retry Needed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = enrollmentState.reason,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(onClick = {
                                onCancel()
                                onDismiss()
                            }) {
                                Text("Cancel")
                            }
                            Button(onClick = onRecordSample) {
                                Text("Retry Sample ${enrollmentState.sampleIndex}")
                            }
                        }
                    }

                    is EnrollmentState.Processing -> {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Calibrating voice features...", style = MaterialTheme.typography.bodyMedium)
                    }

                    is EnrollmentState.Success -> {
                        Text(text = "✓", fontSize = 42.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Enrolled for ${enrollmentState.speakerName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "NOVA is now trained to your voice. Hands-Free mode will activate when earphones are connected.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                        Button(onClick = onDismiss) {
                            Text("Done")
                        }
                    }

                    is EnrollmentState.Error -> {
                        Text(text = "⚠️", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = enrollmentState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            OutlinedButton(onClick = {
                                onCancel()
                                onDismiss()
                            }) {
                                Text("Close")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                val name = speakerNameInput.trim().ifBlank { "Owner" }
                                onStartEnrollment(name)
                            }) {
                                Text("Try Again")
                            }
                        }
                    }
                }
            }
        }
    }
}
