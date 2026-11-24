package com.osaf.jarvis.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.osaf.jarvis.ui.theme.LocalJarvisHudColors

@Composable
fun HudGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    contentPadding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val hud = LocalJarvisHudColors.current
    val strokeColor = borderColor ?: hud.borderCyan
    val shape = RoundedCornerShape(16.dp)

    val innerContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            hud.glassSurface,
                            hud.deepNavy.copy(alpha = 0.88f)
                        )
                    )
                )
                .drawBehind {
                    val corner = 12.dp.toPx()
                    val stroke = 2.dp.toPx()
                    val accent = hud.primaryGlow.copy(alpha = 0.7f)
                    // Top-left HUD bracket
                    drawLine(accent, Offset(0f, corner), Offset(0f, 0f), stroke)
                    drawLine(accent, Offset(0f, 0f), Offset(corner, 0f), stroke)
                    // Bottom-right HUD bracket
                    drawLine(accent, Offset(size.width - corner, size.height), Offset(size.width, size.height), stroke)
                    drawLine(accent, Offset(size.width, size.height - corner), Offset(size.width, size.height), stroke)
                }
                .padding(contentPadding),
            content = content
        )
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, strokeColor),
            content = innerContent
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, strokeColor),
            content = innerContent
        )
    }
}
