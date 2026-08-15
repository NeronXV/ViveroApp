package com.intutec.viveroapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val ViveroShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkLeafGreen,
    primaryContainer = DarkLeafContainer,
    secondary = EarthContainer,
    tertiary = EarthBrown,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = LightText,
    onSurface = LightText,
)

private val LightColorScheme = lightColorScheme(
    primary = LeafGreen,
    onPrimary = OnLeafGreen,
    primaryContainer = LeafContainer,
    onPrimaryContainer = OnLeafContainer,
    secondary = EarthBrown,
    secondaryContainer = EarthContainer,
    tertiary = EarthBrown,
    tertiaryContainer = EarthContainer,
    background = CreamBackground,
    surface = CreamSurface,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = MutedText,
    outline = SoftOutline,
    error = ErrorRed,
)

@Composable
fun ViveroAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ViveroShapes,
        content = content,
    )
}
