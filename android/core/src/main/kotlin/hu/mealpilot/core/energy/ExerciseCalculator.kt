package hu.mealpilot.core.energy

import hu.mealpilot.core.model.Sex
import hu.mealpilot.core.model.UserProfile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Az edzés energiafelhasználásának becslése.
 *
 * A pontosság sorrendje (a legjobbtól):
 *  1. [Method.HEART_RATE] — átlagos pulzussal, Keytel et al. (2005) regresszió.
 *  2. [Method.PERSONALIZED_MET] — MET × a felhasználó saját nyugalmi anyagcseréje.
 *     Ez pontosabb a tankönyvi MET-nél, mert az a 3,5 ml/kg/min-t mindenkire
 *     egyformán feltételezi, holott nehezebb és idősebb embereknél ez felülbecsül.
 *  3. [Method.STANDARD_MET] — klasszikus MET × 3,5 ml/kg/min.
 */
object ExerciseCalculator {

    enum class Method { HEART_RATE, PERSONALIZED_MET, STANDARD_MET }

    data class Result(
        /** Az edzés teljes energiafelhasználása, a nyugalmi anyagcserét is beleértve. */
        val kcalGross: Int,
        /**
         * A nyugalmi anyagcsere fölötti TÖBBLET. Ezt szabad visszaenni, mert a napi
         * kalóriakeret (TDEE) már tartalmazza a pihenő energiaigényt erre az időre is.
         */
        val kcalNet: Int,
        val method: Method,
        val met: Double,
        val note: String,
    )

    /** A felhasználó nyugalmi energiaigénye percenként (1 MET személyre szabva). */
    fun restingKcalPerMinute(profile: UserProfile): Double =
        EnergyCalculator.bmr(profile) / 1440.0

    /**
     * @param avgHeartRate opcionális átlagpulzus; ha megadod, a legpontosabb módszer fut le.
     */
    fun estimate(
        profile: UserProfile,
        exercise: ExerciseType,
        minutes: Int,
        avgHeartRate: Int? = null,
        preferStandardMet: Boolean = false,
    ): Result {
        val mins = max(0, minutes).toDouble()
        val restingPerMin = restingKcalPerMinute(profile)

        if (avgHeartRate != null && avgHeartRate in 60..220) {
            val grossPerMin = keytelKcalPerMinute(profile, avgHeartRate)
            if (grossPerMin > 0) {
                val gross = grossPerMin * mins
                val net = max(0.0, gross - restingPerMin * mins)
                return Result(
                    kcalGross = gross.roundToInt(),
                    kcalNet = net.roundToInt(),
                    method = Method.HEART_RATE,
                    met = grossPerMin / restingPerMin,
                    note = "Pulzus alapján (${avgHeartRate} bpm átlag) — ez a legpontosabb becslés.",
                )
            }
        }

        if (preferStandardMet) {
            val perMin = exercise.met * 3.5 * profile.weightKg / 200.0
            val gross = perMin * mins
            val net = max(0.0, (exercise.met - 1.0) * 3.5 * profile.weightKg / 200.0 * mins)
            return Result(
                kcalGross = gross.roundToInt(),
                kcalNet = net.roundToInt(),
                method = Method.STANDARD_MET,
                met = exercise.met,
                note = "Tankönyvi MET-képlet (3,5 ml/kg/min).",
            )
        }

        val gross = exercise.met * restingPerMin * mins
        val net = max(0.0, (exercise.met - 1.0) * restingPerMin * mins)
        return Result(
            kcalGross = gross.roundToInt(),
            kcalNet = net.roundToInt(),
            method = Method.PERSONALIZED_MET,
            met = exercise.met,
            note = "MET × a saját alapanyagcseréd — pontosabb, mint az általános képlet.",
        )
    }

    /**
     * Keytel et al. (2005) pulzus alapú regresszió, kcal/perc.
     * Edzés közbeni, egyenletes terhelésre validált; nagyon rövid vagy
     * szakaszos terhelésnél kevésbé megbízható.
     */
    fun keytelKcalPerMinute(profile: UserProfile, heartRate: Int): Double {
        val hr = heartRate.toDouble()
        val kg = profile.weightKg
        val age = profile.ageYears.toDouble()
        val kj = when (profile.sex) {
            Sex.MALE -> -55.0969 + 0.6309 * hr + 0.1988 * kg + 0.2017 * age
            Sex.FEMALE -> -20.4022 + 0.4472 * hr - 0.1263 * kg + 0.0740 * age
        }
        return max(0.0, kj / 4.184)
    }

    /** Becsült maximális pulzus (Tanaka et al., 2001) — pontosabb, mint a 220 − életkor. */
    fun estimatedMaxHeartRate(ageYears: Int): Int = (208.0 - 0.7 * ageYears).roundToInt()
}
