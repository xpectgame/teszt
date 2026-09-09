package hu.mealpilot.core.energy

/**
 * MET-értékek a Compendium of Physical Activities (Ainsworth et al., 2011) alapján.
 * A MET a nyugalmi anyagcsere többszöröse: 1 MET = pihenés, 8 MET = nyolcszoros energiaigény.
 */
data class ExerciseType(
    val key: String,
    val hu: String,
    val met: Double,
    val category: Category,
) {
    enum class Category(val hu: String) {
        WALK_RUN("Gyaloglás / futás"),
        CYCLING("Kerékpár"),
        GYM("Terem / erő"),
        SPORT("Sportágak"),
        WATER("Vízi"),
        HOME("Otthon / egyéb"),
    }
}

object MetTable {

    private fun t(key: String, hu: String, met: Double, cat: ExerciseType.Category) =
        ExerciseType(key, hu, met, cat)

    val all: List<ExerciseType> = listOf(
        // Gyaloglás / futás
        t("walk_slow", "Séta, lassú (3,2 km/h)", 2.8, ExerciseType.Category.WALK_RUN),
        t("walk_normal", "Séta, átlagos (5 km/h)", 3.5, ExerciseType.Category.WALK_RUN),
        t("walk_brisk", "Gyors gyaloglás (6,4 km/h)", 5.0, ExerciseType.Category.WALK_RUN),
        t("walk_uphill", "Gyaloglás emelkedőn / túra", 6.0, ExerciseType.Category.WALK_RUN),
        t("hiking", "Túrázás hátizsákkal", 7.3, ExerciseType.Category.WALK_RUN),
        t("run_8", "Kocogás (8 km/h)", 8.3, ExerciseType.Category.WALK_RUN),
        t("run_10", "Futás (10 km/h)", 9.8, ExerciseType.Category.WALK_RUN),
        t("run_12", "Futás (12 km/h)", 11.8, ExerciseType.Category.WALK_RUN),
        t("run_14", "Futás (14 km/h)", 14.5, ExerciseType.Category.WALK_RUN),
        t("stairs", "Lépcsőzés", 8.8, ExerciseType.Category.WALK_RUN),

        // Kerékpár
        t("bike_leisure", "Kerékpár, kényelmes (<16 km/h)", 4.0, ExerciseType.Category.CYCLING),
        t("bike_moderate", "Kerékpár, közepes (19–22 km/h)", 8.0, ExerciseType.Category.CYCLING),
        t("bike_fast", "Kerékpár, gyors (25–30 km/h)", 12.0, ExerciseType.Category.CYCLING),
        t("spinning", "Spinning / szobabicikli, intenzív", 8.8, ExerciseType.Category.CYCLING),

        // Terem / erő
        t("weights_light", "Súlyzós edzés, könnyű", 3.5, ExerciseType.Category.GYM),
        t("weights_hard", "Súlyzós edzés, intenzív", 6.0, ExerciseType.Category.GYM),
        t("circuit", "Köredzés / HIIT", 8.0, ExerciseType.Category.GYM),
        t("crossfit", "CrossFit", 9.0, ExerciseType.Category.GYM),
        t("calisthenics", "Saját testsúlyos edzés", 5.5, ExerciseType.Category.GYM),
        t("rowing_machine", "Evezőgép, közepes", 7.0, ExerciseType.Category.GYM),
        t("elliptical", "Elliptikus tréner", 5.0, ExerciseType.Category.GYM),
        t("yoga", "Jóga", 3.0, ExerciseType.Category.GYM),
        t("pilates", "Pilates", 3.8, ExerciseType.Category.GYM),
        t("stretching", "Nyújtás / mobilizálás", 2.3, ExerciseType.Category.GYM),

        // Sportágak
        t("football", "Foci", 7.0, ExerciseType.Category.SPORT),
        t("basketball", "Kosárlabda", 6.5, ExerciseType.Category.SPORT),
        t("tennis", "Tenisz", 7.3, ExerciseType.Category.SPORT),
        t("squash", "Squash", 12.0, ExerciseType.Category.SPORT),
        t("volleyball", "Röplabda", 4.0, ExerciseType.Category.SPORT),
        t("handball", "Kézilabda", 8.0, ExerciseType.Category.SPORT),
        t("badminton", "Tollaslabda", 5.5, ExerciseType.Category.SPORT),
        t("table_tennis", "Asztalitenisz", 4.0, ExerciseType.Category.SPORT),
        t("martial_arts", "Küzdősport", 10.3, ExerciseType.Category.SPORT),
        t("climbing", "Falmászás", 8.0, ExerciseType.Category.SPORT),
        t("skiing", "Síelés", 7.0, ExerciseType.Category.SPORT),
        t("dancing", "Tánc", 5.0, ExerciseType.Category.SPORT),

        // Vízi
        t("swim_leisure", "Úszás, kényelmes", 5.8, ExerciseType.Category.WATER),
        t("swim_fast", "Úszás, edzés tempó", 9.8, ExerciseType.Category.WATER),
        t("aqua_fit", "Aquafitnesz", 5.3, ExerciseType.Category.WATER),
        t("kayak", "Kajak / kenu", 5.0, ExerciseType.Category.WATER),

        // Otthon / egyéb
        t("housework", "Házimunka, takarítás", 3.3, ExerciseType.Category.HOME),
        t("gardening", "Kertészkedés", 3.8, ExerciseType.Category.HOME),
        t("shopping_walk", "Bevásárlás, pakolás", 2.5, ExerciseType.Category.HOME),
        t("playing_kids", "Játék gyerekekkel, aktív", 4.8, ExerciseType.Category.HOME),
        t("manual_labor", "Nehéz fizikai munka", 6.5, ExerciseType.Category.HOME),
    )

    private val byKey = all.associateBy(ExerciseType::key)

    fun byKey(key: String): ExerciseType? = byKey[key]

    fun byCategory(): Map<ExerciseType.Category, List<ExerciseType>> =
        all.groupBy(ExerciseType::category)

    /** Egyszerű, ékezet-toleráns keresés a mozgás gyors felviteléhez. */
    fun search(query: String): List<ExerciseType> {
        val q = normalize(query)
        if (q.isBlank()) return all
        return all.filter { normalize(it.hu).contains(q) || it.key.contains(q) }
    }

    private fun normalize(s: String): String = s.lowercase()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ö', 'o').replace('ő', 'o')
        .replace('ú', 'u').replace('ü', 'u').replace('ű', 'u')
}
