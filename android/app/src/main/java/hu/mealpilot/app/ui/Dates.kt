package hu.mealpilot.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hu.mealpilot.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Olvasható dátumfelirat a felület nyelvén: „Ma · szeptember 9., szerda".
 *
 * A hónap- és napneveket a JDK adja a beállított területi beállításból — azt a
 * `LanguageStore.wrap` állítja be az Activity indulásakor. Kézzel írt névlistával
 * ugyanez minden új nyelvnél újra kellene, és a ragozást is elrontanánk.
 */
@Composable
fun LocalDate.dayLabel(): String {
    val prefix = when (this) {
        LocalDate.now() -> stringResource(R.string.date_today)
        LocalDate.now().plusDays(1) -> stringResource(R.string.date_tomorrow)
        LocalDate.now().minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> ""
    }
    val pattern = stringResource(R.string.date_pattern)
    return prefix + format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
