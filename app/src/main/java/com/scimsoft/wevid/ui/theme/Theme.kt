package com.scimsoft.wevid.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val WeVidColorScheme = darkColorScheme(
    primary = Coral,
    onPrimary = Paper,
    secondary = Mint,
    onSecondary = Ink,
    background = Color.Transparent,
    onBackground = Paper,
    surface = InkElevated,
    onSurface = Paper,
    onSurfaceVariant = PaperMuted,
    outline = InkLine,
    error = Danger,
    onError = Paper,
)

@Composable
fun WeVidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WeVidColorScheme,
        typography = WeVidTypography,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF0E1628),
                            0.45f to Ink,
                            0.78f to Color(0xFF0C1524),
                            1.0f to Color(0xFF151008),
                        ),
                    ),
                ),
        ) {
            // Soft coral wash top-right — atmosphere without overpowering content.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(CoralSoft, Color.Transparent),
                            center = Offset(920f, -40f),
                            radius = 780f,
                        ),
                    ),
            )
            content()
        }
    }
}
