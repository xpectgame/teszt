package hu.mealpilot.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B3E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F0BD),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF52634F),
    secondaryContainer = Color(0xFFD5E8CE),
    tertiary = Color(0xFF39656B),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFFBFDF7),
    surface = Color(0xFFFBFDF7),
    surfaceVariant = Color(0xFFDDE5DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF99D5A3),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF14522A),
    onPrimaryContainer = Color(0xFFB4F0BD),
    secondary = Color(0xFFB9CCB3),
    secondaryContainer = Color(0xFF3A4B38),
    tertiary = Color(0xFFA1CED4),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF101410),
    surface = Color(0xFF101410),
    surfaceVariant = Color(0xFF414941),
)

/** A kalóriakeret állapotát jelző színek — a felületen több helyen ugyanezt használjuk. */
object BudgetColors {
    val under = Color(0xFF2E7D32)
    val close = Color(0xFFF9A825)
    val over = Color(0xFFC62828)
}

@Composable
fun MealPilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
