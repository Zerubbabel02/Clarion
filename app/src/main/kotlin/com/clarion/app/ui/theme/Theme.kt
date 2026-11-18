package com.clarion.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.clarion.app.R

// Night Map palette, copied from the mockup (docs/mockups/night-map-v2.html, skin "sk1").
private val NightColors = darkColorScheme(
    primary = Color(0xFF9DB2FF),
    onPrimary = Color(0xFF0B0F1A),
    primaryContainer = Color(0xFF1D2540),
    onPrimaryContainer = Color(0xFF9DB2FF),
    background = Color(0xFF0B0F1A),
    onBackground = Color(0xFFEEF2FA),
    surface = Color(0xFF141A2A),
    onSurface = Color(0xFFEEF2FA),
    surfaceVariant = Color(0xFF1D2540),
    onSurfaceVariant = Color(0xFFA3AEC6),
    outline = Color(0xFF2A3350),
    error = Color(0xFFFF9A70),
)

private val DayColors = lightColorScheme(
    primary = Color(0xFF3D4FB8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EAF2),
    onPrimaryContainer = Color(0xFF3D4FB8),
    background = Color(0xFFF2F4F8),
    onBackground = Color(0xFF131A2B),
    surface = Color.White,
    onSurface = Color(0xFF131A2B),
    surfaceVariant = Color(0xFFE6EAF2),
    onSurfaceVariant = Color(0xFF56607A),
    outline = Color(0xFFD5DBE8),
    error = Color(0xFFB5400F),
)

@OptIn(ExperimentalTextApi::class)
private fun manrope(w: Int) = Font(R.font.manrope, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))
val DmMono = FontFamily(Font(R.font.dm_mono_medium, FontWeight.Medium), Font(R.font.dm_mono_medium, FontWeight.Normal))

private fun TextStyle.m() = copy(fontFamily = Manrope)
private val Base = Typography()
private val ClarionType = Typography(
    displayLarge = Base.displayLarge.m(), displayMedium = Base.displayMedium.m(), displaySmall = Base.displaySmall.m(),
    headlineLarge = Base.headlineLarge.m(), headlineMedium = Base.headlineMedium.m(), headlineSmall = Base.headlineSmall.m(),
    titleLarge = Base.titleLarge.m(), titleMedium = Base.titleMedium.m(), titleSmall = Base.titleSmall.m(),
    bodyLarge = Base.bodyLarge.m(), bodyMedium = Base.bodyMedium.m(), bodySmall = Base.bodySmall.m(),
    labelLarge = Base.labelLarge.m(), labelMedium = Base.labelMedium.m(), labelSmall = Base.labelSmall.m(),
)

@Composable
fun ClarionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NightColors else DayColors,
        typography = ClarionType,
        content = content,
    )
}
