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
     * Abszolút alsó kalóriahatár: a nemenkénti klinikai minimum.
     *
     * Az alapanyagcsere SZÁNDÉKOSAN nincs benne ebben a határban. Ülő életmódnál a napi
     * felhasználás az alapanyagcsere 1,2-szerese, tehát az „alapanyagcsere alá soha nem
     * tervezünk" szabály a felhasználók nagy részének heti 0,3–0,4 kg-ra vágta volna az
     * ütemét akkor is, ha ő heti fél kilót állított be. Az ütemről a felhasználó dönt.
     *
     * Ami helyette marad: ez a klinikai minimum, a napi felhasználás [MAX_DEFICIT_RATIO]
     * arányú felső korlátja, és — ha a cél az alapanyagcsere alá kerül — egy
     * figyelmeztetés a [budget] kimenetében. Korlátozás helyett tájékoztatás.
     */
    fun floorKcal(profile: UserProfile): Double =
        if (profile.sex == Sex.MALE) 1500.0 else 1200.0

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
            // MELYIK korlát fogott? A kettő mást jelent a felhasználónak, és mást lehet
            // tenni ellene — de egyik sem a felhasználó hibája. A régi szöveg úgy
            // fogalmazott, hogy „a kért ütem túl agresszív", ami azért hibáztatja, amit
            // sokszor nem is ő állított be: heti fél kiló az app alapértelmezése.
            val achievable = appliedDeficit * 7.0 / KCAL_PER_KG_FAT
            val rateText = "%.2f".format(achievable)
            warnings += if (maxByFloor <= maxByRatio) {
                s(
                    "A napi felhasználásod (${tdeeValue.roundToInt()} kcal) közel van a biztonságos " +
                        "alsó kalóriahatárhoz (${floor.roundToInt()} kcal), és az alá nem tervezünk. " +
                        "Ezért $rateText kg/hét fér bele heti ${"%.2f".format(rate)} kg helyett. " +
                        "Több mozgással gyorsulhat.",
                    "Your daily burn (${tdeeValue.roundToInt()} kcal) is close to the safe minimum " +
                        "intake (${floor.roundToInt()} kcal), and we never plan below that. " +
                        "So $rateText kg/week fits instead of ${"%.2f".format(rate)} kg. " +
                        "More activity would speed it up.",
                )
            } else {
                s(
                    "A biztonságos felső határ a napi felhasználásod " +
                        "${(MAX_DEFICIT_RATIO * 100).roundToInt()}%-a, ezért $rateText kg/hét fér bele " +
                        "heti ${"%.2f".format(rate)} kg helyett. Több mozgással gyorsulhat.",
                    "The safe maximum is ${(MAX_DEFICIT_RATIO * 100).roundToInt()}% of your daily burn, " +
                        "so $rateText kg/week fits instead of ${"%.2f".format(rate)} kg. " +
                        "More activity would speed it up.",
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

        // A cél az alapanyagcsere ALÁ kerülhet — és ez rendben van: az ütemet a
        // felhasználó választja, nem mi. Régen ezt a kód kemény korlátként kezelte, ami
        // ülő életmódnál majdnem mindenkit heti 0,3–0,4 kg-ra fogott vissza. Korlátozás
        // helyett most megmondjuk, mivel jár, és rábízzuk a döntést.
        if (targetKcal < bmrValue - 1) {
            warnings += s(
                "A napi célod ($targetKcal kcal) az alapanyagcseréd " +
                    "(${bmrValue.roundToInt()} kcal) alatt van — ennyi kell a választott ütemhez. " +
                    "Hetekig tartva izomvesztéssel és lassuló anyagcserével járhat: tartsd magasan " +
                    "a fehérjét, erősíts, és ha sokáig így maradsz, beszélj orvossal. Lassabb " +
                    "ütemmel vagy több mozgással elkerülhető.",
                "Your daily target ($targetKcal kcal) is below your basal metabolic rate " +
                    "(${bmrValue.roundToInt()} kcal) — that is what the pace you chose needs. " +
                    "Kept up for weeks it can cost muscle and slow your metabolism: keep protein " +
                    "high, do strength training, and see a doctor if it lasts. A slower pace or " +
                    "more activity avoids it.",
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
        // A referenciasúly nem a mai testsúly: a felesleges zsír nem kér fehérjét.
        // Három forrás, csökkenő pontossági sorrendben:
        //
        //   1. Ismert testzsír → zsírmentes tömeg + 10%. Ez a legpontosabb, és a
        //      BMI-becslés nem is szólhat bele.
        //   2. Ismert célsúly → a célsúly, mert oda tartunk.
        //   3. Egyik sem → a mai súly, de legfeljebb a normál BMI-sáv felső végéhez
        //      (BMI 25) tartozó súly.
        //
        // A harmadik pont javítás: e nélkül egy 160 cm-es, 120 kg-os felhasználó a mai
        // súlyára kapott 2,2 g/ttkg fehérjét (264 g) és 0,8 g/ttkg zsírt (96 g). A kettő
        // együtt 1920 kcal — több, mint a napi kerete. Az alábbi 90%-os visszaskálázás
        // ilyenkor nem hibát jelzett, hanem csendben szétverte az arányokat: a
        // szénhidrátra alig 40-50 g maradt, épp abból, amiből az étrend összeáll.
        val referenceKg = profile.leanBodyMassKg?.times(1.1)
            ?: profile.targetWeightKg?.takeIf { it in 35.0..250.0 }
            ?: min(profile.weightKg, bmi25WeightKg(profile))

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
     * A normál BMI-sáv felső végéhez (BMI 25) tartozó testsúly az adott magassághoz.
     * A makrók referenciasúlyának felső korlátja, ha se testzsír, se célsúly nem ismert.
     */
    fun bmi25WeightKg(profile: UserProfile): Double {
        val heightM = profile.heightCm / 100.0
        return 25.0 * heightM * heightM
    }

    /** Hány nap alatt érhető el a célsúly a jelenlegi deficittel. Null, ha nincs cél vagy nincs deficit. */
    fun daysToTarget(profile: UserProfile, budget: EnergyBudget): Int? {
        val target = profile.targetWeightKg ?: return null
        val toLose = profile.weightKg - target
        if (toLose <= 0 || budget.appliedDeficit <= 0) return null
        return (toLose * KCAL_PER_KG_FAT / budget.appliedDeficit).roundToInt()
    }
}
