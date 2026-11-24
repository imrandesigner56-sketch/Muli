package com.osaf.jarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.osaf.jarvis.database.HudThemeMode

val OrbitronFontFamily = FontFamily(Font(R.font.orbitron, FontWeight.Bold))
val SpaceGroteskFontFamily = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
val JetBrainsMonoFontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

@Immutable
data class JarvisHudColors(
    val spaceBlack: Color,
    val deepNavy: Color,
    val glassSurface: Color,
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val onlineGreen: Color,
    val warningAmber: Color,
    val errorCrimson: Color,
    val offlineSteel: Color,
    val borderCyan: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

val DefaultHudColors = JarvisHudColors(
    spaceBlack = Color(0xFF02060D),
    deepNavy = Color(0xFF071222),
    glassSurface = Color(0xD90A192F),
    primaryGlow = Color(0xFF00E5FF),
    secondaryGlow = Color(0xFF2979FF),
    onlineGreen = Color(0xFF00FFA3),
    warningAmber = Color(0xFFFFB300),
    errorCrimson = Color(0xFFFF3D57),
    offlineSteel = Color(0xFF53708A),
    borderCyan = Color(0x5500E5FF),
    textPrimary = Color(0xFFEAF6FF),
    textSecondary = Color(0xFF8AAEC9)
)

val LocalJarvisHudColors = staticCompositionLocalOf { DefaultHudColors }

val JarvisTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 1.5.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.6.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun JarvisTheme(
    themeMode: HudThemeMode = HudThemeMode.CYBER_CYAN,
    content: @Composable () -> Unit
) {
    val hudColors = when (themeMode) {
        HudThemeMode.CYBER_CYAN -> DefaultHudColors
        HudThemeMode.ARC_BLUE -> DefaultHudColors.copy(
            primaryGlow = Color(0xFF38BDF8),
            secondaryGlow = Color(0xFF6366F1),
            borderCyan = Color(0x5538BDF8)
        )
        HudThemeMode.STEALTH_DARK -> DefaultHudColors.copy(
            spaceBlack = Color(0xFF010306),
            deepNavy = Color(0xFF060B14),
            primaryGlow = Color(0xFF7DD3FC),
            secondaryGlow = Color(0xFF3B82F6),
            borderCyan = Color(0x407DD3FC)
        )
    }

    val colorScheme = darkColorScheme(
        primary = hudColors.primaryGlow,
        onPrimary = Color(0xFF00141A),
        primaryContainer = Color(0xFF0B2A45),
        onPrimaryContainer = hudColors.textPrimary,
        secondary = hudColors.secondaryGlow,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF10243E),
        onSecondaryContainer = hudColors.textPrimary,
        tertiary = hudColors.onlineGreen,
        background = hudColors.spaceBlack,
        onBackground = hudColors.textPrimary,
        surface = hudColors.deepNavy,
        onSurface = hudColors.textPrimary,
        surfaceVariant = Color(0xFF0F2038),
        onSurfaceVariant = hudColors.textSecondary,
        outline = hudColors.borderCyan,
        error = hudColors.errorCrimson,
        onError = Color.White
    )

    CompositionLocalProvider(LocalJarvisHudColors provides hudColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JarvisTypography,
            content = content
        )
    }
}
