package hu.mealpilot.app.billing

import com.android.billingclient.api.ProductDetails

/**
 * Melyik előfizetési ajánlatot indítsuk, és melyik ára jelenjen meg.
 *
 * Egy előfizetéshez több ajánlat tartozhat: az alapcsomag, és mellette az ingyenes
 * próbaidőszak vagy a bevezető ár. A Play már kiszűri, amire a felhasználó nem
 * jogosult — a maradékból viszont nekünk kell választanunk, és a sorrendjükre nincs
 * garancia.
 *
 * A korábbi kód mindkét helyen `firstOrNull()`-t használt. Amíg egyetlen ajánlat volt,
 * ez működött; a próbaidőszak bekapcsolásával viszont a felhasználó azt kaphatta
 * volna, amit a Play éppen elsőnek ad vissza — akár próbaidőszak nélkül.
 *
 * A döntés azért él a Play osztályaitól függetlenül, mert azok nem példányosíthatók
 * teszthez: így a szabály önmagában ellenőrizhető.
 */

/**
 * A legkedvezőbb ajánlat indexe.
 *
 * @param offers ajánlatonként a fázisok ára mikroegységben, a Play sorrendjében
 * @return az index, vagy -1 ha nincs ajánlat
 */
internal fun bestOfferIndex(offers: List<List<Long>>): Int {
    if (offers.isEmpty()) return -1
    return offers.indices.sortedWith(
        compareBy(
            // Elsődlegesen az, amiben van ingyenes fázis: ez a próbaidőszak.
            { if (offers[it].any { price -> price == 0L }) 0 else 1 },
            // Azonos esetben az olcsóbb visszatérő ár.
            { offers[it].lastOrNull() ?: Long.MAX_VALUE },
        ),
    ).first()
}

/**
 * A VISSZATÉRŐ fázis indexe — ezt az árat kell kiírni.
 *
 * Az első fázis ingyenes próbaidőszaknál nulla forint. Azt kiírva az app azt
 * állítaná, hogy az előfizetés ingyenes.
 *
 * @param recurrenceModes fázisonként a [ProductDetails.RecurrenceMode] érték
 */
internal fun recurringPhaseIndex(recurrenceModes: List<Int>): Int {
    if (recurrenceModes.isEmpty()) return 0
    val infinite = recurrenceModes.indexOfFirst { it == ProductDetails.RecurrenceMode.INFINITE_RECURRING }
    // Ha egyik fázis sem jelöli magát végtelenül ismétlődőnek, az utolsó a visszatérő.
    return if (infinite >= 0) infinite else recurrenceModes.lastIndex
}
