package hu.mealpilot.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LightColors = lightColorScheme(
    primary = Plate.green,
    onPrimary = Plate.card,
    primaryContainer = Plate.greenSoft,
    onPrimaryContainer = Plate.green,
    secondary = Plate.clay,
    onSecondary = Plate.card,
    secondaryContainer = Plate.claySoft,
    onSecondaryContainer = Plate.clay,
    tertiary = Plate.clay,
    background = Plate.paper,
    onBackground = Plate.ink,
    surface = Plate.card,
    onSurface = Plate.ink,
    // A kártya a fehér, a krém alap pedig az, ami MÖGÖTTE van — ezért lesz a
    // surfaceVariant krém, nem szürke. A rajta lévő másodlagos szöveg a „halvány
    // tinta": a felületen minden kiegészítő felirat ezt a színt kapja.
    surfaceVariant = Plate.paper,
    onSurfaceVariant = Plate.ink2,
    surfaceContainer = Plate.card,
    surfaceContainerLow = Plate.card,
    surfaceContainerHigh = Plate.paper,
    surfaceContainerHighest = Plate.paper,
    outline = Plate.ink2,
    outlineVariant = Plate.line,
    error = Plate.error,
    onError = Plate.card,
)

private val DarkColors = darkColorScheme(
    primary = Plate.greenDark,
    onPrimary = Plate.paperDark,
    primaryContainer = Plate.greenSoftDark,
    onPrimaryContainer = Plate.greenDark,
    secondary = Plate.clayDark,
    onSecondary = Plate.paperDark,
    secondaryContainer = Plate.claySoftDark,
    onSecondaryContainer = Plate.clayDark,
    tertiary = Plate.clayDark,
    background = Plate.paperDark,
    onBackground = Plate.inkDark,
    surface = Plate.cardDark,
    onSurface = Plate.inkDark,
    surfaceVariant = Plate.paperDark,
    onSurfaceVariant = Plate.ink2Dark,
    surfaceContainer = Plate.cardDark,
    surfaceContainerLow = Plate.cardDark,
    surfaceContainerHigh = Plate.lineDark,
    surfaceContainerHighest = Plate.lineDark,
    outline = Plate.ink2Dark,
    outlineVariant = Plate.lineDark,
    error = Plate.errorDark,
    onError = Plate.paperDark,
)

/**
 * Sötét mód-e. Az étkezésszínek nem férnek bele a Material sémába (öt önálló szín),
 * viszont sötét háttéren másik változatuk kell — ezért kell egy kérdés, amit a
 * felület bárhol feltehet.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * A „Tányér" irány.
 *
 * A dinamikus szín szándékosan NINCS bekötve. A Material 3 azt a háttérképből
 * származtatja, tehát minden telefonon más lenne — a krém–zöld–agyag hármas pedig
 * pont az, amitől ez az app felismerhető. Ezt a `docs/DESIGN-BRIEF.md` is így kérte.
 */
@Composable
fun MealPilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PlateTypography,
            shapes = PlateShapes,
            content = content,
        )
    }
}
