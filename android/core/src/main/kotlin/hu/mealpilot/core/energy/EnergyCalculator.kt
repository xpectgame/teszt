package hu.mealpilot.core.energy

import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.ActivityLevel
import hu.mealpilot.core.model.DailyTarget
import hu.mealpilot.core.model.MacroPreset
import hu.mealpilot.core.model.Sex
import hu.mealpilot.core.model.UserProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 1 kg testzsír ≈ 7700 kcal (a fogyás ütemének kalóriára váltásához). */
const val KCAL_PER_KG_FAT = 7700.0

/** Milyen szigorú lehet a deficit — a TDEE ennyi százalékánál nem megyünk lejjebb. */
const val MAX_DEFICIT_RATIO = 0.25

data class EnergyBudget(
    val bmr: Int,
    val tdee: Int,
    /** A kért ütemből adódó, még nem korlátozott deficit. */
    val requestedDeficit: Int,
    /** A ténylegesen alkalmazott deficit a biztonsági korlátok után. */
    val appliedDeficit: Int,
    val target: DailyTarget,
    val warnings: List<String>,
) {
    /** Reális heti fogyás a ténylegesen alkalmazott deficit alapján, kg-ban. */
    val expectedRateKgPerWeek: Double get() = appliedDeficit * 7.0 / KCAL_PER_KG_FAT

    val wasCapped: Boolean get() = appliedDeficit < requestedDeficit
}

/**
 * Alapanyagcsere és napi kalóriakeret számítás.
 *
 * A napi mozgást egyetlen bemenet fedi: a profil [ActivityLevel] szorzója, ami az
 * edzést is tartalmazza. Az app szándékosan nem vezet edzésnaplót — az étkezésről szól.
 */
object EnergyCalculator {

    /** Mifflin-St Jeor (1990) — a testzsírszázalék ismerete nélkül ez a legpontosabb bevált képlet. */
    fun bmrMifflinStJeor(profile: UserProfile): Double {
        val base = 10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * profile.ageYears
        return when (profile.sex) {
            Sex.MALE -> base + 5.0
            Sex.FEMALE -> base - 161.0
        }
    }

    /** Katch-McArdle — ismert testzsírszázalék esetén pontosabb, mert a zsírmentes tömegre épít. */
    fun bmrKatchMcArdle(leanBodyMassKg: Double): Double = 370.0 + 21.6 * leanBodyMassKg

    fun bmr(profile: UserProfile): Double =
        profile.leanBodyMassKg?.let { bmrKatchMcArdle(it) } ?: bmrMifflinStJeor(profile)

    fun tdee(profile: UserProfile): Double = bmr(profile) * profile.activityLevel.factor

    /**
     * Abszolút alsó kalóriahatár. Két szabály közül a szigorúbb (magasabb) győz:
     * nemenkénti klinikai minimum, illetve az alapanyagcsere.
     */
    fun floorKcal(profile: UserProfile): Double {
        val sexFloor = if (profile.sex == Sex.MALE) 1500.0 else 1200.0
        return max(sexFloor, bmr(profile))
    }

    fun budget(
        profile: UserProfile,
        language: AppLanguage = AppLanguage.DEFAULT,
    ): EnergyBudget {
        val bmrValue = bmr(profile)
        val tdeeValue = tdee(profile)
        val warnings = mutableListOf<String>()
        // A figyelmeztetés a felhasználónak szól, nem a naplónak: a felület nyelvén kell lennie.
        fun s(hungarian: String, english: String) = if (language == AppLanguage.EN) english else hungarian

        val rate = profile.targetRateKgPerWeek.coerceIn(0.0, 1.5)
        if (profile.targetRateKgPerWeek > 1.0) {
            warnings += s(
                "Heti 1 kg-nál gyorsabb fogyás tartósan nem ajánlott — 1,0 kg/hétre korlátoztam.",
                "Losing more than 1 kg a week is not advisable long term — I capped it at 1.0 kg/week.",
            )
        }
        val requestedDeficit = (rate.coerceAtMost(1.0) * KCAL_PER_KG_FAT / 7.0).roundToInt()

        val floor = floorKcal(profile)
        val maxByRatio = tdeeValue * MAX_DEFICIT_RATIO
        val maxByFloor = max(0.0, tdeeValue - floor)
        val allowedDeficit = min(maxByRatio, maxByFloor)

        val appliedDeficit = min(requestedDeficit.toDouble(), allowedDeficit).coerceAtLeast(0.0)
        if (appliedDeficit < requestedDeficit - 1) {
            // MELYIK korlát fogott? A kettő nagyon mást jelent a felhasználónak.
            //
            // Az alapanyagcsere-korlát ülő életmódnál MINDIG fog: a napi felhasználás
            // ilyenkor az alapanyagcsere 1,2-szerese, tehát a kettő közé fér a teljes
            // mozgástér. Az app alapértelmezett üteme (0,5 kg/hét) ennél nagyobb
            // deficitet kérne, vagyis minden ülő életmódú felhasználó megkapta ezt a
            // figyelmeztetést — az első képernyőn, a SAJÁT alapértelmezésünkre.
            //
            // A régi szöveg ezt úgy fogalmazta, hogy „a kért ütem túl agresszív", ami a
            // felhasználót hibáztatja azért, amit nem ő állított be. Nem is igaz: heti
            // fél kiló nem agresszív cél. Ami szűkös, az a mozgás és az alapanyagcsere
            // közötti sáv.
            val achievable = appliedDeficit * 7.0 / KCAL_PER_KG_FAT
            val rateText = "%.2f".format(achievable)
            warnings += if (maxByFloor <= maxByRatio) {
                s(
                    "Ezen a mozgásszinten a napi felhasználásod (${tdeeValue.roundToInt()} kcal) közel van " +
                        "az alapanyagcserédhez (${bmrValue.roundToInt()} kcal), és az alá nem tervezünk. " +
                        "Ezért $rateText kg/hét fér bele heti ${"%.2f".format(rate)} kg helyett. " +
                        "Több mozgással gyorsulhat.",
                    "At this activity level your daily burn (${tdeeValue.roundToInt()} kcal) is close to " +
                        "your BMR (${bmrValue.roundToInt()} kcal), and we never plan below that. " +
                        "So $rateText kg/week fits instead of ${"%.2f".format(rate)} kg. " +
                        "More activity would speed it up.",
                )
            } else {
                s(
                    "A biztonságos felső határ a napi felhasználásod " +
                        "${(MAX_DEFICIT_RATIO * 100).roundToInt()}%-a, ezért $rateText kg/hét fér bele " +
                        "heti ${"%.2f".format(rate)} kg helyett.",
                    "The safe maximum is ${(MAX_DEFICIT_RATIO * 100).roundToInt()}% of your daily burn, " +
                        "so $rateText kg/week fits instead of ${"%.2f".format(rate)} kg.",
                )
            }
        }

        // Az alsó határ a CÉLRA vonatkozik, nem csak a deficitre. Korábban a kód a
        // deficitet nullázta le, de a célt nem emelte meg — ha a TDEE maga a határ alatt
        // volt (kis zsírmentes tömeg, vagy elgépelt testzsír), a felhasználó a saját
        // klinikai minimuma alatti kalóriacélt kapott, miközben a figyelmeztetés az
        // ellenkezőjét állította.
        val rawTarget = tdeeValue - appliedDeficit
        val targetKcal = max(rawTarget, floor).roundToInt()
        if (rawTarget < floor - 1) {
            warnings += s(
                "A megadott adatokból a napi felhasználásod ${tdeeValue.roundToInt()} kcal, ami a " +
                    "biztonságos alsó határ alatt van. A célt ${targetKcal} kcal-ra emeltem, és " +
                    "ezen a szinten nem tervezek deficitet. Ha ez meglepő, ellenőrizd a testadataidat.",
                "From the data you gave, your daily expenditure is ${tdeeValue.roundToInt()} kcal, " +
                    "which is below the safe minimum. I raised the target to ${targetKcal} kcal and " +
                    "will not plan a deficit at this level. If that looks wrong, check your body data.",
            )
        }
        val target = macroTarget(profile, targetKcal)

        if (profile.bmi < 20.0) {
            warnings += s(
                "A BMI-d ${"%.1f".format(profile.bmi)} — ezen a szinten a fogyás helyett inkább " +
                    "a testösszetétel javítása (fehérje + erőedzés) az értelmes cél.",
                "Your BMI is ${"%.1f".format(profile.bmi)} — at this level the sensible goal is " +
                    "improving body composition (protein + strength training), not losing weight.",
            )
        }

        return EnergyBudget(
            bmr = bmrValue.roundToInt(),
            tdee = tdeeValue.roundToInt(),
            requestedDeficit = requestedDeficit,
            appliedDeficit = appliedDeficit.roundToInt(),
            target = target,
            warnings = warnings,
        )
    }

    /**
     * Makróelosztás: a fehérje és a zsír testsúly-arányos alsó küszöb, a maradék szénhidrát.
     * Deficitben a fehérje az izomvesztés elleni legfontosabb tényező, ezért ez fix.
     */
    fun macroTarget(profile: UserProfile, targetKcal: Int): DailyTarget {
        val preset = profile.macroPreset
        // A referenciasúly a zsírmentes tömeg + 10%, illetve túlsúly esetén a célsúly:
        // így nem számolunk irreálisan sok fehérjét nagy testzsírszázaléknál.
        val referenceKg = profile.leanBodyMassKg?.times(1.1)
            ?: profile.targetWeightKg?.takeIf { it in 35.0..250.0 }
            ?: profile.weightKg

        var proteinG = (preset.proteinPerKg * referenceKg).roundToInt()
        var fatG = (preset.fatPerKg * referenceKg).roundToInt()

        // Ha a fehérje + zsír már túllépné a keretet, arányosan visszaskálázzuk.
        val floorKcal = proteinG * 4 + fatG * 9
        if (floorKcal > targetKcal * 0.90) {
            val scale = (targetKcal * 0.90) / floorKcal
            proteinG = (proteinG * scale).roundToInt()
            fatG = (fatG * scale).roundToInt()
        }

        val carbsG = max(0, ((targetKcal - proteinG * 4 - fatG * 9) / 4.0).roundToInt())
        // Rost: 14 g / 1000 kcal (amerikai és hazai ajánlás), 25 g-os alsó korláttal.
        val fiberG = max(25, (targetKcal / 1000.0 * 14).roundToInt())

        return DailyTarget(kcal = targetKcal, proteinG = proteinG, carbsG = carbsG, fatG = fatG, fiberG = fiberG)
    }

    /** Hány nap alatt érhető el a célsúly a jelenlegi deficittel. Null, ha nincs cél vagy nincs deficit. */
    fun daysToTarget(profile: UserProfile, budget: EnergyBudget): Int? {
        val target = profile.targetWeightKg ?: return null
        val toLose = profile.weightKg - target
        if (toLose <= 0 || budget.appliedDeficit <= 0) return null
        return (toLose * KCAL_PER_KG_FAT / budget.appliedDeficit).roundToInt()
    }
}
