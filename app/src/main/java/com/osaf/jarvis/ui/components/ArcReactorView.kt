package com.osaf.jarvis.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.osaf.jarvis.ReactorState
import com.osaf.jarvis.ui.theme.LocalJarvisHudColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorView(
    state: ReactorState,
    rmsLevel: Float,
    assistantName: String,
    size: Dp = 220.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hud = LocalJarvisHudColors.current

    val targetColor = when (state) {
        ReactorState.IDLE -> hud.primaryGlow
        ReactorState.LISTENING -> if (hud.isSimpleTheme) hud.primaryGlow else Color(0xFF00FFFF)
        ReactorState.THINKING -> hud.secondaryGlow
        ReactorState.SPEAKING -> hud.onlineGreen
        ReactorState.OFFLINE -> hud.offlineSteel
        ReactorState.ERROR -> hud.errorCrimson
    }
    val coreColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(280),
        label = "reactorColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "reactorLoop")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRing"
    )
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRing"
    )
    val pulseFactor by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val speedMultiplier = when (state) {
        ReactorState.THINKING -> 2.4f
        ReactorState.LISTENING -> 1.5f
        ReactorState.SPEAKING -> 1.6f
        ReactorState.OFFLINE -> 0.35f
        ReactorState.ERROR -> 1.9f
        ReactorState.IDLE -> 1.0f
    }

    val smoothRms by animateFloatAsState(
        targetValue = when (state) {
            ReactorState.LISTENING -> rmsLevel.coerceIn(0.15f, 1f)
            ReactorState.SPEAKING -> (0.35f + 0.45f * kotlin.math.abs(sin(outerAngle * 0.14f))).coerceIn(0.2f, 0.9f)
            ReactorState.THINKING -> 0.25f
            else -> 0.08f
        },
        animationSpec = spring(stiffness = 320f),
        label = "rms"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = "$assistantName orb, status ${state.label}. Tap to trigger voice input."
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = (this.size.minDimension / 2f) * 0.94f

            if (hud.isSimpleTheme) {
                // Clean minimal Gemini-like voice orb
                val haloScale = (0.78f + smoothRms * 0.22f) * pulseFactor
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = if (hud.isLight) 0.20f else 0.28f),
                            coreColor.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxR * haloScale.coerceAtMost(1.06f)
                    ),
                    radius = maxR * haloScale.coerceAtMost(1.06f),
                    center = center
                )

                // Soft outer subtle ring
                drawCircle(
                    color = coreColor.copy(alpha = if (state == ReactorState.LISTENING) 0.55f else 0.24f),
                    radius = maxR * (0.72f + smoothRms * 0.08f),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Smooth rotating accent arc when thinking/listening
                if (state == ReactorState.THINKING || state == ReactorState.LISTENING || state == ReactorState.SPEAKING) {
                    val rot = (outerAngle * speedMultiplier) % 360f
                    val arcSize = Size(maxR * 1.44f, maxR * 1.44f)
                    val topLeft = Offset(center.x - arcSize.width / 2f, center.y - arcSize.height / 2f)
                    drawArc(
                        brush = Brush.sweepGradient(listOf(coreColor, hud.secondaryGlow, Color.Transparent)),
                        startAngle = rot,
                        sweepAngle = 210f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Clean center pill circle
                drawCircle(
                    color = hud.deepNavy,
                    radius = maxR * 0.56f,
                    center = center
                )
                drawCircle(
                    color = coreColor.copy(alpha = 0.5f),
                    radius = maxR * 0.56f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            } else {
                // Classic HUD Reactor
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = 0.28f * pulseFactor),
                            coreColor.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxR * 1.05f
                    ),
                    radius = maxR * 1.05f,
                    center = center
                )

                drawCircle(
                    color = coreColor.copy(alpha = 0.22f),
                    radius = maxR * 0.96f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                val rot1 = (outerAngle * speedMultiplier) % 360f
                val arcRect1 = Size(maxR * 1.76f, maxR * 1.76f)
                val topLeft1 = Offset(center.x - arcRect1.width / 2f, center.y - arcRect1.height / 2f)
                for (i in 0 until 4) {
                    drawArc(
                        color = coreColor.copy(alpha = 0.78f),
                        startAngle = rot1 + i * 90f,
                        sweepAngle = 56f,
                        useCenter = false,
                        topLeft = topLeft1,
                        size = arcRect1,
                        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                val rot2 = (innerAngle * speedMultiplier) % 360f
                val arcRect2 = Size(maxR * 1.44f, maxR * 1.44f)
                val topLeft2 = Offset(center.x - arcRect2.width / 2f, center.y - arcRect2.height / 2f)
                for (i in 0 until 3) {
                    drawArc(
                        color = coreColor.copy(alpha = 0.55f),
                        startAngle = rot2 + i * 120f,
                        sweepAngle = 78f,
                        useCenter = false,
                        topLeft = topLeft2,
                        size = arcRect2,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                val barCount = 44
                val waveBaseRadius = maxR * 0.56f
                val waveMaxExtent = maxR * 0.16f
                for (i in 0 until barCount) {
                    val theta = (2.0 * PI * i / barCount) + Math.toRadians(rot1.toDouble() * 0.2)
                    val harmonic = kotlin.math.abs(sin(i * 0.7 + outerAngle * 0.12)).toFloat()
                    val amp = waveBaseRadius + (waveMaxExtent * smoothRms * (0.35f + 0.65f * harmonic))
                    val start = Offset(
                        center.x + (waveBaseRadius * cos(theta)).toFloat(),
                        center.y + (waveBaseRadius * sin(theta)).toFloat()
                    )
                    val end = Offset(
                        center.x + (amp * cos(theta)).toFloat(),
                        center.y + (amp * sin(theta)).toFloat()
                    )
                    drawLine(
                        color = coreColor.copy(alpha = if (state == ReactorState.OFFLINE) 0.35f else 0.88f),
                        start = start,
                        end = end,
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                drawCircle(
                    color = coreColor.copy(alpha = 0.85f),
                    radius = maxR * 0.48f,
                    center = center,
                    style = Stroke(width = 2.6.dp.toPx())
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (state == ReactorState.OFFLINE) 0.35f else 0.9f),
                            coreColor.copy(alpha = 0.55f),
                            coreColor.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxR * 0.45f * pulseFactor
                    ),
                    radius = maxR * 0.45f * pulseFactor,
                    center = center
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (hud.isSimpleTheme) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = coreColor,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = hud.textPrimary
                )
                Text(
                    text = when (state) {
                        ReactorState.SPEAKING -> "Tap to interrupt"
                        ReactorState.LISTENING -> "Tap to stop"
                        else -> "Tap to talk"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = hud.textSecondary
                )
            } else {
                Text(
                    text = assistantName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = coreColor
                )
            }
        }
    }
}
