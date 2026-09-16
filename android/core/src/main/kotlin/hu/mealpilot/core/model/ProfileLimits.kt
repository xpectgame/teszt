package hu.mealpilot.core.model

/**
 * A profiladatok elfogadható tartományai.
 *
 * Azért itt, és nem a beviteli mezőkben: a testadatok két helyen szerkeszthetők — a
 * profilűrlapon és a súlyrögzítő kártyán —, és a kettő szétcsúszott. A profilűrlap
 * gondosan szorított, a súlykártya semmit nem ellenőrzött, így oda a testsúly is
 * beírható volt a testzsír mezőbe. Egy 80 kg-os embernél ez 80%-os testzsírt jelentett,
 * abból 16 kg zsírmentes tömeget, és 984 kcal-os napi célt a helyes 1846 helyett.
 *
 * Egy forrás, két felület — így nem tud újra szétcsúszni.
 */
object ProfileLimits {
    val AGE_YEARS = 14.0..100.0
    val HEIGHT_CM = 120.0..230.0
    val WEIGHT_KG = 35.0..300.0

    /**
     * A testzsírszázalék felső határa szándékosan 70: efölött a zsírmentes tömeg olyan
     * kicsi lenne, hogy a Katch-McArdle képlet értelmetlen értéket adna. 100 fölött
     * pedig egyenesen negatívat — és onnan a napi kalóriacél is negatív lesz.
     */
    val BODY_FAT_PERCENT = 3.0..70.0

    fun isValidBodyFat(value: Double?): Boolean = value == null || value in BODY_FAT_PERCENT

    fun isValidWeight(value: Double?): Boolean = value != null && value in WEIGHT_KG
}
