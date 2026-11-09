package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PaoComCarneLightColorScheme = lightColorScheme(
    primary = PaoRed,
    onPrimary = PaoSurface,
    primaryContainer = PaoCream,
    onPrimaryContainer = PaoAnthracite,
    secondary = PaoAnthracite,
    onSecondary = PaoSurface,
    secondaryContainer = PaoBorderLight,
    onSecondaryContainer = PaoAnthracite,
    tertiary = PaoRedLight,
    onTertiary = PaoSurface,
    background = PaoBackground,
    onBackground = PaoAnthracite,
    surface = PaoSurface,
    onSurface = PaoAnthracite,
    surfaceVariant = PaoCreamLight,
    onSurfaceVariant = PaoAnthraciteMuted,
    outline = PaoBorder,
    outlineVariant = PaoBorderLight,
    error = StatusRed,
    onError = PaoSurface
)

@Composable
fun PaoComCarneTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PaoComCarneLightColorScheme,
        typography = Typography,
        content = content
    )
}
