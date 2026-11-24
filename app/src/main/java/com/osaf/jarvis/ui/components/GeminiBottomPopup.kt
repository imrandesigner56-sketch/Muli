package com.osaf.jarvis.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.osaf.jarvis.ReactorState
import com.osaf.jarvis.commands.PendingConfirmation
import com.osaf.jarvis.ui.theme.LocalJarvisHudColors

@Composable
fun GeminiBottomPopup(
    visible: Boolean,
    assistantName: String,
    reactorState: ReactorState,
    rmsLevel: Float,
    partialTranscript: String,
    lastTranscript: String,
    lastResponse: String,
    errorMessage: String?,
    pendingConfirmation: PendingConfirmation?,
    isListening: Boolean,
    isSpeaking: Boolean,
    onMicClick: () -> Unit,
    onStopSpeaking: () -> Unit,
    onConfirmAction: () -> Unit,
    onCancelAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hud = LocalJarvisHudColors.current
    val pulseTransition = rememberInfiniteTransition(label = "geminiGlow")
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(220)),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(180)),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color(0xF5071222),
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        hud.secondaryGlow.copy(alpha = glowAlpha),
                        hud.primaryGlow.copy(alpha = glowAlpha),
                        hud.onlineGreen.copy(alpha = glowAlpha * 0.85f)
                    )
                )
            ),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gemini_bottom_popup")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Drag handle & glowing top indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(hud.primaryGlow.copy(alpha = 0.65f))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val orbScale = if (isListening) (1f + rmsLevel * 0.25f) else 1f
                        Box(
                            modifier = Modifier
                                .size((30 * orbScale).dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(hud.primaryGlow, hud.secondaryGlow.copy(alpha = 0.25f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = assistantName,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = assistantName,
                                style = MaterialTheme.typography.titleMedium,
                                color = hud.textPrimary
                            )
                            Text(
                                text = when {
                                    isListening -> "Listening..."
                                    reactorState == ReactorState.THINKING -> "Thinking..."
                                    isSpeaking -> "Speaking..."
                                    else -> "Ready"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = hud.primaryGlow
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSpeaking) {
                            IconButton(onClick = onStopSpeaking) {
                                Icon(
                                    imageVector = Icons.Default.VolumeOff,
                                    contentDescription = "Stop speaking",
                                    tint = hud.warningAmber
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("bottom_popup_close")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close assistant popup",
                                tint = hud.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Transcript / Response body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val liveHeard = if (isListening && partialTranscript.isNotBlank()) {
                        partialTranscript
                    } else {
                        lastTranscript
                    }
                    if (liveHeard.isNotBlank()) {
                        Text(
                            text = "\"$liveHeard\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = hud.textSecondary
                        )
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = hud.warningAmber
                        )
                    } else {
                        Text(
                            text = lastResponse,
                            style = MaterialTheme.typography.bodyLarge,
                            color = hud.textPrimary
                        )
                    }
                }

                if (pendingConfirmation != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onConfirmAction,
                            colors = ButtonDefaults.buttonColors(containerColor = hud.primaryGlow)
                        ) {
                            Text("Confirm", color = Color.Black)
                        }
                        OutlinedButton(onClick = onCancelAction) {
                            Text("Cancel", color = hud.textPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Centered Gemini-style glowing mic button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = onMicClick,
                        modifier = Modifier
                            .size(58.dp)
                            .border(
                                width = 2.dp,
                                color = if (isListening) hud.errorCrimson else hud.primaryGlow,
                                shape = CircleShape
                            ),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isListening) {
                                hud.errorCrimson.copy(alpha = 0.24f)
                            } else {
                                hud.primaryGlow.copy(alpha = 0.18f)
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop listening" else "Speak",
                            tint = if (isListening) hud.errorCrimson else hud.primaryGlow,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
