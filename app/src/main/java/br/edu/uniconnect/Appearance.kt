package br.edu.uniconnect

import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppearancePreferences {
    fun dark(context: Context): Boolean = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("dark", false)
    fun setDark(context: Context, dark: Boolean) = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putBoolean("dark", dark).apply()
}

private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)
private val Gray = Color(0xFFE7E7E7)

@Composable
fun UniConnectTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = White, onPrimary = Black, primaryContainer = Color(0xFF202020), onPrimaryContainer = White,
        background = Black, onBackground = White, surface = Color(0xFF101010), onSurface = White,
        surfaceVariant = Color(0xFF242424), onSurfaceVariant = Gray, outline = Color(0xFFBDBDBD), error = Color(0xFFFF8A80)
    ) else lightColorScheme(
        primary = Black, onPrimary = White, primaryContainer = Color(0xFFF0F0F0), onPrimaryContainer = Black,
        background = White, onBackground = Black, surface = White, onSurface = Black,
        surfaceVariant = Color(0xFFF4F4F4), onSurfaceVariant = Color(0xFF333333), outline = Color(0xFF555555), error = Color(0xFFB00020)
    )
    MaterialTheme(colorScheme = colors, content = content)
}
