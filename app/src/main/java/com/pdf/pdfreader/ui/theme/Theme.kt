package com.pdf.pdfreader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pdf.pdfreader.data.local.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = LumenDarkPrimary,
    primaryContainer = LumenDarkPrimaryContainer,
    onPrimaryContainer = LumenDarkOnPrimaryContainer,
    secondary = LumenDarkSecondary,
    tertiary = Pink80,
    background = LumenDarkBackground,
    surface = LumenDarkSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = LumenDarkSurfaceVariant,
    onSurfaceVariant = Color.LightGray
)

private val LightColorScheme = lightColorScheme(
    primary = LumenPrimary,
    primaryContainer = LumenPrimaryContainer,
    onPrimaryContainer = LumenOnPrimaryContainer,
    secondary = LumenSecondary,
    tertiary = Pink40,
    background = LumenBackground,
    surface = LumenSurface,
    onBackground = Color.Black,
    onSurface = Color.Black,
    surfaceVariant = LumenSurfaceVariant,
    onSurfaceVariant = Color.DarkGray
)

@Composable
fun PDFReaderTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    dynamicColor: Boolean = false, // Disabled to enforce Lumen branding
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

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
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
