package hu.mealpilot.core.i18n

/**
 * Az app nyelve.
 *
 * Nem csak a feliratokat jelenti: az étrend NYELVE is ez. A fogásnevek, a hozzávalók és
 * a bevásárlólista is a választott nyelven készül, mert a tervezőnek ezen a nyelven adunk
 * utasítást — és mert egy magyar hozzávalólistával angolul nem lehet bevásárolni.
 *
 * Ezért fontos, hogy a kizárás-ellenőrzés kulcsszavai is nyelvenként megvannak: egy angol
 * étrendben a „búza" sosem szerepelne, a „wheat" viszont igen. Ha a nyelv váltana, de a
 * kulcsszavak nem, az allergiaszűrés némán megszűnne működni.
 */
enum class AppLanguage(val tag: String, val selfName: String) {
    HU("hu", "Magyar"),
    EN("en", "English");

    val isHungarian: Boolean get() = this == HU

    companion object {
        val DEFAULT = HU

        /** A rendszer nyelvéből választ; ismeretlen nyelvnél angol, nem magyar. */
        fun fromSystemTag(tag: String?): AppLanguage {
            val short = tag?.trim()?.take(2)?.lowercase()
            return entries.firstOrNull { it.tag == short } ?: EN
        }

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag?.trim()?.take(2)?.lowercase() } ?: DEFAULT
    }
}

/**
 * Kétnyelvű címkét hordozó típus. Az enumok ezt valósítják meg, így a fordítás hiánya
 * fordítási hiba, nem futásidejű üres szöveg.
 */
interface Localized {
    val hu: String
    val en: String
}

fun Localized.label(language: AppLanguage): String =
    if (language == AppLanguage.EN) en else hu

/** Kétnyelvű szövegdarab ott, ahol nem enum hordozza. */
data class Text(val hu: String, val en: String) {
    operator fun get(language: AppLanguage): String = if (language == AppLanguage.EN) en else hu
}
