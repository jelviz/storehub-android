package ir.dinal.storehub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DinalPlum = Color(0xFF2D163F)
val DinalPurple = Color(0xFF6E3EB5)
val DinalRose = Color(0xFFC75A8D)
val DinalGold = Color(0xFFD6AA57)
val DinalCream = Color(0xFFFFF8F1)
val DinalInk = Color(0xFF241E28)
val DinalMint = Color(0xFF3A9C8D)

private val LightColors = lightColorScheme(
    primary = DinalPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEDFFF),
    onPrimaryContainer = DinalPlum,
    secondary = DinalRose,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD8E8),
    onSecondaryContainer = Color(0xFF4C1830),
    tertiary = DinalGold,
    onTertiary = Color(0xFF382A0D),
    background = DinalCream,
    onBackground = DinalInk,
    surface = Color.White,
    onSurface = DinalInk,
    surfaceVariant = Color(0xFFF4EDF6),
    outline = Color(0xFF8B7B91)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD8B8FF),
    onPrimary = DinalPlum,
    secondary = Color(0xFFFFB1D0),
    tertiary = Color(0xFFFFD68B),
    background = Color(0xFF171119),
    surface = Color(0xFF211925),
    onSurface = Color(0xFFF5ECF7),
    surfaceVariant = Color(0xFF3A2E3F)
)

@Composable
fun DinalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
