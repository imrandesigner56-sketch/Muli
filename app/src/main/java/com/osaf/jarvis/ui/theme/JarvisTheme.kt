package com.osaf.jarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
    val textSecondary: Color,
    val onPrimaryAccent: Color,
    val isSimpleTheme: Boolean = true,
    val isLight: Boolean = false
)

val SimpleDarkColors = JarvisHudColors(
    spaceBlack = Color(0xFF0E1117),
    deepNavy = Color(0xFF171B24),
    glassSurface = Color(0xFF1D222E),
    primaryGlow = Color(0xFF7EB6FF),
    secondaryGlow = Color(0xFF9D8CFF),
    onlineGreen = Color(0xFF4ADE80),
    warningAmber = Color(0xFFFBBF24),
    errorCrimson = Color(0xFFF87171),
    offlineSteel = Color(0xFF64748B),
    borderCyan = Color(0xFF2B3345),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    onPrimaryAccent = Color(0xFF091526),
    isSimpleTheme = true,
    isLight = false
)

val SimpleLightColors = JarvisHudColors(
    spaceBlack = Color(0xFFF6F8FC),
    deepNavy = Color(0xFFFFFFFF),
    glassSurface = Color(0xFFFFFFFF),
    primaryGlow = Color(0xFF2563EB),
    secondaryGlow = Color(0xFF6366F1),
    onlineGreen = Color(0xFF16A34A),
    warningAmber = Color(0xFFD97706),
    errorCrimson = Color(0xFFDC2626),
    offlineSteel = Color(0xFF64748B),
    borderCyan = Color(0xFFDCE4F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    onPrimaryAccent = Color.White,
    isSimpleTheme = true,
    isLight = true
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
    textSecondary = Color(0xFF8AAEC9),
    onPrimaryAccent = Color(0xFF00141A),
    isSimpleTheme = false,
    isLight = false
)

val LocalJarvisHudColors = staticCompositionLocalOf { SimpleDarkColors }

val SimpleTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
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
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

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
    themeMode: HudThemeMode = HudThemeMode.SIMPLE_DARK,
    content: @Composable () -> Unit
) {
    val hudColors = when (themeMode) {
        HudThemeMode.SIMPLE_DARK -> SimpleDarkColors
        HudThemeMode.SIMPLE_LIGHT -> SimpleLightColors
        HudThemeMode.CYBER_CYAN -> DefaultHudColors
    }

    val colorScheme = if (hudColors.isLight) {
        lightColorScheme(
            primary = hudColors.primaryGlow,
            onPrimary = hudColors.onPrimaryAccent,
            primaryContainer = Color(0xFFE0ECFF),
            onPrimaryContainer = hudColors.textPrimary,
            secondary = hudColors.secondaryGlow,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE8EAFF),
            onSecondaryContainer = hudColors.textPrimary,
            tertiary = hudColors.onlineGreen,
            background = hudColors.spaceBlack,
            onBackground = hudColors.textPrimary,
            surface = hudColors.deepNavy,
            onSurface = hudColors.textPrimary,
            surfaceVariant = Color(0xFFEEF2F8),
            onSurfaceVariant = hudColors.textSecondary,
            outline = hudColors.borderCyan,
            error = hudColors.errorCrimson,
            onError = Color.White
        )
    } else {
        darkColorScheme(
            primary = hudColors.primaryGlow,
            onPrimary = hudColors.onPrimaryAccent,
            primaryContainer = if (hudColors.isSimpleTheme) Color(0xFF23324D) else Color(0xFF0B2A45),
            onPrimaryContainer = hudColors.textPrimary,
            secondary = hudColors.secondaryGlow,
            onSecondary = Color.White,
            secondaryContainer = if (hudColors.isSimpleTheme) Color(0xFF252B3B) else Color(0xFF10243E),
            onSecondaryContainer = hudColors.textPrimary,
            tertiary = hudColors.onlineGreen,
            background = hudColors.spaceBlack,
            onBackground = hudColors.textPrimary,
            surface = hudColors.deepNavy,
            onSurface = hudColors.textPrimary,
            surfaceVariant = if (hudColors.isSimpleTheme) Color(0xFF222836) else Color(0xFF0F2038),
            onSurfaceVariant = hudColors.textSecondary,
            outline = hudColors.borderCyan,
            error = hudColors.errorCrimson,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalJarvisHudColors provides hudColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (hudColors.isSimpleTheme) SimpleTypography else JarvisTypography,
            content = content
        )
    }
}
