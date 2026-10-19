package com.openclipflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Czarno-biały interfejs (jak w OpenPDF): akcent = biel, czerwień dla nagrywania i usuwania. */
object Palette {
    val Amber = Color(0xFFFFFFFF)
    val Record = Color(0xFFFF3B47)
    val Ink = Color(0xFF000000)
    val Panel = Color(0xFF000000)
    val PanelHigh = Color(0xFF1C1C1C)
    val Raised = Color(0xFF262626)
    val TextDim = Color(0xB3FFFFFF)
    val TextFaint = Color(0x66FFFFFF)
    val Hairline = Color(0x1FFFFFFF)

    /** Kolory klipów na osi czasu – stonowane, żeby miniatury były czytelne. */
    val VideoClip = Color(0xFF2A3F5C)
    val ImageClip = Color(0xFF3D4A2E)
    val AudioClip = Color(0xFF214A40)
    val TitleClip = Color(0xFF5A3A66)
    val Waveform = Color(0xFF7FE0C2)
    val Playhead = Color(0xFFFFFFFF)
    val Marker = Color(0xFFFFD54F)
    val Selection = Color(0xFFFFFFFF)

    val TitleColors = listOf(
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFD54F.toInt(), 0xFFFF5252.toInt(),
        0xFF40C4FF.toInt(), 0xFF69F0AE.toInt(), 0xFFFF80AB.toInt(), 0xFFB388FF.toInt(),
    )
    val AccentColors = listOf(
        0xCC000000.toInt(), 0xCCFFFFFF.toInt(), 0xE6E53935.toInt(), 0xE61E88E5.toInt(),
        0xE643A047.toInt(), 0xE6FB8C00.toInt(), 0xE68E24AA.toInt(), 0x00000000,
    )
}

private val scheme = darkColorScheme(
    primary = Palette.Amber,
    onPrimary = Palette.Ink,
    secondary = Color(0xFFE0E0E0),
    onSecondary = Palette.Ink,
    background = Palette.Ink,
    onBackground = Color.White,
    surface = Palette.Panel,
    onSurface = Color.White,
    surfaceVariant = Palette.PanelHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.Panel,
    surfaceContainerHigh = Palette.PanelHigh,
    surfaceContainerHighest = Palette.PanelHigh,
    outline = Color(0x33FFFFFF),
    error = Palette.Record,
)

val MonoDigits = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp)

@Composable
fun OpenClipFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
