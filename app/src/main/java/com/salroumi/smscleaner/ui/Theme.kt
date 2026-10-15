package com.salroumi.smscleaner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val Light = lightColorScheme(
    primary = Color(0xFF1E5AA8),
    secondary = Color(0xFF2EAD6B),
    error = Color(0xFFC62828),
    background = Color(0xFFF7F9FC),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9CC3FF),
    secondary = Color(0xFF7FD8A6),
    error = Color(0xFFFF8A80),
)

/** App theme. The UI is Arabic, so layout is always right-to-left. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
