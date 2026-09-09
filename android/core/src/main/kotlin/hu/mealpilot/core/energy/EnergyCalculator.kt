package hu.mealpilot.core.energy

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
 * Fontos: a [ActivityLevel] szorzó az edzés NÉLKÜLI napi mozgást fedi, a naplózott
 * edzések kalóriája ezen felül, külön adódik hozzá ([ExerciseCalculator]).
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

    fun budget(profile: UserProfile): EnergyBudget {
        val bmrValue = bmr(profile)
        val tdeeValue = tdee(profile)
        val warnings = mutableListOf<String>()

        val rate = profile.targetRateKgPerWeek.coerceIn(0.0, 1.5)
        if (profile.targetRateKgPerWeek > 1.0) {
            warnings += "Heti 1 kg-nál gyorsabb fogyás tartósan nem ajánlott — 1,0 kg/hétre korlátoztam."
        }
        val requestedDeficit = (rate.coerceAtMost(1.0) * KCAL_PER_KG_FAT / 7.0).roundToInt()

        val floor = floorKcal(profile)
        val maxByRatio = tdeeValue * MAX_DEFICIT_RATIO
        val maxByFloor = max(0.0, tdeeValue - floor)
        val allowedDeficit = min(maxByRatio, maxByFloor)

        val appliedDeficit = min(requestedDeficit.toDouble(), allowedDeficit).coerceAtLeast(0.0)
        if (appliedDeficit < requestedDeficit - 1) {
            warnings += "A kért ütem túl agresszív lenne ehhez a testsúlyhoz: a deficitet " +
                "${appliedDeficit.roundToInt()} kcal/napra mérsékeltem " +
                "(max. a TDEE ${(MAX_DEFICIT_RATIO * 100).roundToInt()}%-a, és nem megyünk az alapanyagcsere alá)."
        }

        val targetKcal = (tdeeValue - appliedDeficit).roundToInt()
        val target = macroTarget(profile, targetKcal)

        if (profile.bmi < 20.0) {
            warnings += "A BMI-d ${"%.1f".format(profile.bmi)} — ezen a szinten a fogyás helyett inkább " +
                "a testösszetétel javítása (fehérje + erőedzés) az értelmes cél."
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

    /**
     * A napi kalóriakeret az elégetett mozgás visszaszámolásával.
     * @param eatBackRatio a naplózott edzéskalória hány százalékát írjuk jóvá (0.0–1.0).
     *   Az alapérték 0.5, mert az edzésbecslések rendszeresen felülbecsülnek.
     */
    fun adjustedDailyKcal(baseTargetKcal: Int, exerciseKcalNet: Int, eatBackRatio: Double = 0.5): Int =
        baseTargetKcal + (exerciseKcalNet * eatBackRatio.coerceIn(0.0, 1.0)).roundToInt()

    /** Hány nap alatt érhető el a célsúly a jelenlegi deficittel. Null, ha nincs cél vagy nincs deficit. */
    fun daysToTarget(profile: UserProfile, budget: EnergyBudget): Int? {
        val target = profile.targetWeightKg ?: return null
        val toLose = profile.weightKg - target
        if (toLose <= 0 || budget.appliedDeficit <= 0) return null
        return (toLose * KCAL_PER_KG_FAT / budget.appliedDeficit).roundToInt()
    }
}
