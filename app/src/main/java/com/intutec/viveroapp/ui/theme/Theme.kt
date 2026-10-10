package com.intutec.viveroapp.ui.theme

import android.os.Build
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
    onSecondaryContainer = Color(0xFF7A4805),
    tertiary = LeafGreenLight,
    tertiaryContainer = LeafContainer,
    background = CreamBackground,
    surface = CreamSurface,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = CreamBackground,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = MutedText,
    outline = SoftOutline,
    outlineVariant = SoftOutline,
    error = ErrorRed,
)

@Composable
fun ViveroAppTheme(
    darkTheme: Boolean = false,
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.windowActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = colorScheme.background.luminance() > 0.5f
                    isAppearanceLightNavigationBars = colorScheme.surface.luminance() > 0.5f
                }
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ViveroShapes,
        content = content,
    )
}

private tailrec fun Context.windowActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.windowActivity()
    else -> null
}
