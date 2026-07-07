package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = GeometricPrimary,
    onPrimary = GeometricOnPrimary,
    secondary = GeometricSecondary,
    tertiary = GeometricTertiary,
    background = GeometricBg,
    surface = GeometricSurface,
    onBackground = GeometricTextPrimary,
    onSurface = GeometricTextPrimary
  )

private val LightColorScheme =
  darkColorScheme( // Keep it dark as the "Geometric Balance" design is natively a dark-themed visual experience
    primary = GeometricPrimary,
    onPrimary = GeometricOnPrimary,
    secondary = GeometricSecondary,
    tertiary = GeometricTertiary,
    background = GeometricBg,
    surface = GeometricSurface,
    onBackground = GeometricTextPrimary,
    onSurface = GeometricTextPrimary
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color disabled by default to preserve the exact Geometric Balance palette
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
