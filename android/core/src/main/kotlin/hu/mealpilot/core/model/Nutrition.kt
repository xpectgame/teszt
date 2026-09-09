package hu.mealpilot.core.model

import kotlin.math.roundToInt

/** Egy étel / nap / terv tápanyagtartalma. Minden mező grammban, kivéve [kcal] és [sodiumMg]. */
data class Nutrients(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
    val saturatedFatG: Double = 0.0,
    val sodiumMg: Double = 0.0,
) {
    operator fun plus(other: Nutrients) = Nutrients(
        kcal = kcal + other.kcal,
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
        fiberG = fiberG + other.fiberG,
        sugarG = sugarG + other.sugarG,
        saturatedFatG = saturatedFatG + other.saturatedFatG,
        sodiumMg = sodiumMg + other.sodiumMg,
    )

    operator fun times(factor: Double) = Nutrients(
        kcal * factor, proteinG * factor, carbsG * factor, fatG * factor,
        fiberG * factor, sugarG * factor, saturatedFatG * factor, sodiumMg * factor,
    )

    /**
     * A megadott makrókból számolt energia (4/4/9 kcal/g). Ha ez erősen eltér a
     * [kcal] mezőtől, az AI válasza valószínűleg inkonzisztens.
     */
    val kcalFromMacros: Double get() = proteinG * 4 + carbsG * 4 + fatG * 9

    fun isConsistent(tolerance: Double = 0.20): Boolean {
        if (kcal <= 0) return false
        return kotlin.math.abs(kcalFromMacros - kcal) / kcal <= tolerance
    }

    companion object {
        val ZERO = Nutrients()
        fun sum(items: Iterable<Nutrients>): Nutrients = items.fold(ZERO, Nutrients::plus)
    }
}

/** Napi cél: kalória + makrók grammban. */
data class DailyTarget(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int,
) {
    val proteinKcal get() = proteinG * 4
    val carbsKcal get() = carbsG * 4
    val fatKcal get() = fatG * 9

    fun proteinShare(): Int = if (kcal == 0) 0 else (proteinKcal * 100.0 / kcal).roundToInt()
    fun carbsShare(): Int = if (kcal == 0) 0 else (carbsKcal * 100.0 / kcal).roundToInt()
    fun fatShare(): Int = if (kcal == 0) 0 else (fatKcal * 100.0 / kcal).roundToInt()
}
