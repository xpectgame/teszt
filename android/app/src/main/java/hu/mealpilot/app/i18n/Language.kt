package hu.mealpilot.app.i18n

import androidx.compose.runtime.compositionLocalOf
import hu.mealpilot.core.i18n.AppLanguage

/**
 * Az aktuális nyelv a Compose fában.
 *
 * `compositionLocalOf`, nem `staticCompositionLocalOf`: nyelvváltáskor újra kell rajzolni
 * mindent, ami szöveget mutat — pont ez a lényeg.
 */
val LocalAppLanguage = compositionLocalOf { AppLanguage.DEFAULT }
