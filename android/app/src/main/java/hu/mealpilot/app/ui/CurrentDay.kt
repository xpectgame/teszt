package hu.mealpilot.app.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.ZoneId

/**
 * A mai nap, éjfélkor magától továbblépve.
 *
 * MIÉRT NEM ELÉG EGY `LocalDate.now()`. Egy ViewModel születésekor kiolvasott dátum
 * ott ragad: aki nyitva hagyja az appot éjfélkor, másnap is a tegnapot látja. A
 * képernyők nem életciklus-tudatosan gyűjtenek, tehát a háttérbe kerülés sem indítja
 * újra a folyamot. Ez a hiba a Ma képernyőn már egyszer megvolt és javítva lett —
 * a Haladás képernyő viszont ugyanabba a csapdába esett, mert a megoldás egyetlen
 * ViewModel belsejében élt, ahol nem lehetett rátalálni.
 *
 * MIÉRT OLVASSA ÚJRA A DÁTUMOT, ÉS NEM LÉPTET EGYET. Ha a telefon energiatakarékos
 * módban aludt, az ébredés késhet, és egy cikluson belül akár több nap is eltelhet.
 */
fun currentDayFlow(): Flow<LocalDate> = flow {
    while (true) {
        val day = LocalDate.now()
        emit(day)
        val nextMidnight = day.plusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        delay((nextMidnight - System.currentTimeMillis()).coerceAtLeast(1_000L))
    }
}
