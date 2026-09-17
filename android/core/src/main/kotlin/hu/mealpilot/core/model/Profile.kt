package hu.mealpilot.core.model

import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.i18n.Localized

/** Biológiai nem — kizárólag az anyagcsere-képletek (Mifflin-St Jeor, Keytel) bemenete. */
enum class Sex { MALE, FEMALE }

/**
 * Napi mozgásszint, az edzéssel EGYÜTT.
 *
 * Az app nem vezet edzésnaplót, ezért ennek az egy kérdésnek kell lefednie a teljes
 * mozgást — a munkát, a közlekedést és a sportot is. Ezek a klasszikus szorzók; a
 * címkék szándékosan edzésszámban beszélnek, mert arra könnyebb válaszolni, mint egy
 * elvont „közepesen aktív" besorolásra.
 */
enum class ActivityLevel(
    val factor: Double,
    override val hu: String,
    override val en: String,
) : Localized {
    SEDENTARY(1.20, "Ülő életmód, alig mozgok", "Mostly sitting, little exercise"),
    LIGHT(1.375, "Heti 1–3 edzés vagy sok gyaloglás", "1–3 workouts a week, or lots of walking"),
    MODERATE(1.55, "Heti 3–5 edzés", "3–5 workouts a week"),
    HIGH(1.725, "Heti 6–7 edzés vagy fizikai munka", "6–7 workouts a week, or physical work"),
    EXTREME(1.90, "Napi kétszeri edzés vagy nehéz fizikai munka", "Twice-daily training or heavy labour");
}

/**
 * Étrendi stílus — és az, hogy MIT JELENT.
 *
 * A [rule] azért van, mert a stílus eddig puszta CÍMKEKÉNT ment a promptba: a modell
 * annyit látott, hogy „Vegán", a többit neki kellett kitalálnia. A gépi ellenőrzés
 * ugyan kizárásokat is származtat belőle (lásd DietRestriction.impliedBy), de vannak
 * dolgok, amiket kulcsszóval nem lehet elkapni — a méz a legjobb példa: a vegán
 * étrendből kimarad, de nincs olyan allergia-kizárás, ami lefedné.
 *
 * Amit a szabály kimond, azt a modell betartja; amit a kulcsszólista fog, azt
 * ellenőrizni is tudjuk. A kettő együtt kell.
 */
enum class DietStyle(
    override val hu: String,
    override val en: String,
    /** Egy mondat a promptba arról, mit zár ki ez a stílus. Üres, ha nem korlátoz. */
    val ruleHu: String = "",
    val ruleEn: String = "",
) : Localized {
    OMNIVORE("Mindenevő", "Omnivore"),
    VEGETARIAN(
        "Vegetáriánus", "Vegetarian",
        "Hús, hal és tenger gyümölcsei nem szerepelhetnek, és állati eredetű zselatin " +
            "sem (kocsonya, aszpik, gumicukor). Tojás és tejtermék használható.",
        "No meat, fish or seafood, and no animal gelatin either (aspic, jelly, gummy sweets). " +
            "Eggs and dairy are fine.",
    ),
    VEGAN(
        "Vegán", "Vegan",
        "Semmilyen állati eredetű összetevő: hús, hal, tojás, tejtermék, MÉZ és zselatin " +
            "sem. A méz is kimarad — erre külön figyelj, mert könnyű elfelejteni.",
        "No animal-derived ingredient at all: no meat, fish, eggs, dairy, HONEY or gelatin. " +
            "Honey is excluded too — watch for that one, it is easy to forget.",
    ),
    PESCATARIAN(
        "Pescatariánus", "Pescatarian",
        "Hús nem szerepelhet, hal és tenger gyümölcsei igen.",
        "No meat; fish and seafood are fine.",
    ),
    LOW_CARB(
        "Alacsony szénhidrát", "Low carb",
        "A szénhidrát elsősorban zöldségből és kevés gyümölcsből jöjjön; kenyér, tészta, " +
            "rizs és burgonya csak kis adagban.",
        "Carbohydrate should come mainly from vegetables and a little fruit; bread, pasta, " +
            "rice and potato only in small portions.",
    ),
    MEDITERRANEAN(
        "Mediterrán", "Mediterranean",
        "Olívaolaj, hal, hüvelyesek, zöldség és teljes értékű gabona domináljon; " +
            "vörös hús ritkán.",
        "Lean on olive oil, fish, pulses, vegetables and whole grains; red meat rarely.",
    );

    fun rule(language: AppLanguage): String = if (language == AppLanguage.EN) ruleEn else ruleHu
}

enum class MacroPreset(
    override val hu: String,
    override val en: String,
    val proteinPerKg: Double,
    val fatPerKg: Double,
) : Localized {
    BALANCED("Kiegyensúlyozott", "Balanced", 1.8, 0.9),
    HIGH_PROTEIN("Magas fehérje", "High protein", 2.2, 0.8),
    LOW_CARB("Alacsony szénhidrát", "Low carb", 2.0, 1.3);
}

/**
 * Felhasználói profil. A [bodyFatPercent] opcionális: ha megvan, a pontosabb
 * Katch-McArdle képlet fut le a Mifflin-St Jeor helyett.
 */
data class UserProfile(
    val name: String = "",
    val sex: Sex = Sex.MALE,
    val ageYears: Int = 30,
    val heightCm: Double = 178.0,
    val weightKg: Double = 85.0,
    val bodyFatPercent: Double? = null,
    val activityLevel: ActivityLevel = ActivityLevel.LIGHT,
    val targetWeightKg: Double? = null,
    /** Kívánt fogyás üteme kg/hét. 0.25–1.0 közé ajánlott. */
    val targetRateKgPerWeek: Double = 0.5,
    val dietStyle: DietStyle = DietStyle.OMNIVORE,
    val macroPreset: MacroPreset = MacroPreset.HIGH_PROTEIN,
    val mealsPerDay: Int = 4,
    /** Allergiák, intoleranciák és étrendi döntések a bekapcsoláskori felmérésből. */
    val restrictions: Set<DietRestriction> = emptySet(),
    /** Szabad szöveges testreszabás: utált ételek, konyha, időkeret, büdzsé. */
    val preferences: String = "",
    /** Étkezési idősávok "HH:mm" formában, hossza legfeljebb [mealsPerDay]. */
    val mealTimes: List<String> = listOf("07:30", "12:30", "16:00", "19:30"),
) {
    val bmi: Double get() = weightKg / ((heightCm / 100.0) * (heightCm / 100.0))

    /**
     * Zsírmentes testtömeg, ha ismert a testzsírszázalék.
     *
     * Az ÉRTELMES tartományon kívüli érték itt null-t ad, nem hibás számot: onnan a
     * számítás a Mifflin-St Jeor képletre esik vissza, ami csak a súlyt és a magasságot
     * használja. Enélkül egy 100 fölötti testzsír negatív zsírmentes tömeget, abból
     * negatív alapanyagcserét, végül NEGATÍV napi kalóriacélt adna.
     *
     * A beviteli mezők is szorítanak, de egy egészségügyi számítás ne függjön attól,
     * hogy minden felület gondos volt-e.
     */
    val leanBodyMassKg: Double?
        get() = bodyFatPercent
            ?.takeIf { it in ProfileLimits.BODY_FAT_PERCENT }
            ?.let { weightKg * (1.0 - it / 100.0) }

    /**
     * A ténylegesen érvényes kizárások: amit a felhasználó kipipált, plusz ami az
     * étrendi stílusból következik (vegánnál a tej és a tojás is tiltott, akkor is,
     * ha külön nem jelölte be).
     */
    val effectiveRestrictions: Set<DietRestriction>
        get() = restrictions + DietRestriction.impliedBy(dietStyle)
}
