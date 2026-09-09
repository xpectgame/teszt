package hu.mealpilot.core.shopping

import hu.mealpilot.core.ai.Aisle
import hu.mealpilot.core.ai.AiIngredient
import kotlin.math.roundToInt

/** Egy összevont bevásárlólista-tétel. */
data class ShoppingItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val aisle: Aisle,
    /** Hány fogásban szerepel — a listán tájékoztatásként megjeleníthető. */
    val usedInMeals: Int,
    val notes: List<String> = emptyList(),
) {
    /** Emberi olvasásra formázott mennyiség (g → kg, ml → l nagy tételeknél). */
    fun displayQuantity(): String = when {
        unit == "g" && quantity >= 1000 -> "${trim(quantity / 1000)} kg"
        unit == "ml" && quantity >= 1000 -> "${trim(quantity / 1000)} l"
        else -> "${trim(quantity)} $unit"
    }

    private fun trim(value: Double): String {
        val rounded = (value * 100).roundToInt() / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString().trimEnd('0').trimEnd('.')
    }
}

/**
 * Hozzávalókból bevásárlólistát épít: azonos alapanyagokat összevon, a mértékegységeket
 * közös alapra hozza, és polc szerint csoportosít.
 */
object ShoppingListBuilder {

    /** Mennyiségszorzók a közös alapegységre (g, ml). */
    private val UNIT_TO_BASE: Map<String, Pair<String, Double>> = mapOf(
        "g" to ("g" to 1.0),
        "gramm" to ("g" to 1.0),
        "dkg" to ("g" to 10.0),
        "deka" to ("g" to 10.0),
        "kg" to ("g" to 1000.0),
        "ml" to ("ml" to 1.0),
        "dl" to ("ml" to 100.0),
        "cl" to ("ml" to 10.0),
        "l" to ("ml" to 1000.0),
        "liter" to ("ml" to 1000.0),
    )

    fun build(
        ingredients: List<AiIngredient>,
        includePantryStaples: Boolean = false,
    ): List<ShoppingItem> {
        val buckets = LinkedHashMap<String, MutableList<Normalized>>()

        for (raw in ingredients) {
            if (raw.name.isBlank()) continue
            if (raw.pantryStaple && !includePantryStaples) continue
            val norm = normalize(raw)
            buckets.getOrPut(norm.groupKey) { mutableListOf() }.add(norm)
        }

        return buckets.values.map { group ->
            val first = group.first()
            ShoppingItem(
                name = first.displayName,
                quantity = round2(group.sumOf { it.quantity }),
                unit = first.unit,
                aisle = first.aisle,
                usedInMeals = group.size,
                notes = group.mapNotNull { it.note.takeIf(String::isNotBlank) }.distinct(),
            )
        }.sortedWith(compareBy({ it.aisle.ordinal }, { it.name }))
    }

    /** Polconként csoportosított lista — a bolti sorrendet követi. */
    fun buildGrouped(
        ingredients: List<AiIngredient>,
        includePantryStaples: Boolean = false,
    ): Map<Aisle, List<ShoppingItem>> =
        build(ingredients, includePantryStaples).groupBy(ShoppingItem::aisle)

    private data class Normalized(
        val groupKey: String,
        val displayName: String,
        val quantity: Double,
        val unit: String,
        val aisle: Aisle,
        val note: String,
    )

    private fun normalize(raw: AiIngredient): Normalized {
        val cleanUnit = raw.unit.trim().lowercase()
        val (unit, factor) = UNIT_TO_BASE[cleanUnit] ?: (cleanUnit.ifBlank { "db" } to 1.0)
        val name = raw.name.trim().lowercase().removeSuffix(",")
        return Normalized(
            // Ugyanaz az alapanyag más mértékegységben külön sor marad (pl. "tojás 2 db" vs "tojás 100 g").
            groupKey = "$name|$unit",
            displayName = name.replaceFirstChar(Char::uppercaseChar),
            quantity = raw.quantity * factor,
            unit = unit,
            aisle = Aisle.fromRaw(raw.aisle),
            note = raw.note.trim(),
        )
    }

    private fun round2(value: Double) = (value * 100).roundToInt() / 100.0
}
