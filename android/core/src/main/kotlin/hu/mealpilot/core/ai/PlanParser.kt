package hu.mealpilot.core.ai

import kotlinx.serialization.json.Json

/**
 * A modell válaszából kiszedi és beolvassa a JSON-t.
 *
 * Szándékosan megengedő: a modell néha ```json kerítésbe teszi a választ, vagy
 * kísérőmondatot ír elé — ezeket egy szigorú parser miatt nem akarjuk elbukni.
 */
object PlanParser {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    fun parsePlan(raw: String): Result<AiPlanResponse> = runCatching {
        json.decodeFromString(AiPlanResponse.serializer(), extractJsonObject(raw))
    }

    fun parseDay(raw: String): Result<AiDayResponse> = runCatching {
        json.decodeFromString(AiDayResponse.serializer(), extractJsonObject(raw))
    }

    fun parseChat(raw: String): Result<AiChatResponse> = runCatching {
        json.decodeFromString(AiChatResponse.serializer(), extractJsonObject(raw))
    }

    fun parseEstimate(raw: String): Result<AiMealEstimate> = runCatching {
        json.decodeFromString(AiMealEstimate.serializer(), extractJsonObject(raw))
    }

    /**
     * Kivágja az első teljes, kiegyensúlyozott zárójelezésű JSON objektumot.
     * A stringen belüli kapcsos zárójeleket és az escape-elt idézőjeleket is kezeli.
     */
    fun extractJsonObject(raw: String): String {
        val text = raw.trim().removeCodeFence()
        val start = text.indexOf('{')
        require(start >= 0) { "A válasz nem tartalmaz JSON objektumot." }

        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        throw IllegalArgumentException("A JSON objektum nincs lezárva (a válasz valószínűleg csonka).")
    }

    /**
     * A körülölelő ```-kerítés levétele.
     *
     * A nyitó kerítés után állhat nyelvjelölés (```json), és utána jöhet SORTÖRÉS, de
     * jöhet azonnal a tartalom is. Korábban a sortörés volt a határ, és ha nem volt
     * benne, az egész válaszból üres szöveg lett — a hívó pedig azt a félrevezető
     * hibát kapta, hogy a válasz nem tartalmaz JSON-t. Egy kész terv veszett el vele,
     * és a javító kör pénzbe kerül.
     *
     * A nyelvjelölést nem itt vágjuk le: az objektum kikeresése úgyis az első `{`-nél
     * kezdődik, tehát ami előtte áll, az magától kimarad.
     */
    private fun String.removeCodeFence(): String {
        if (!startsWith("```")) return this
        return drop(3).substringBeforeLast("```").trim()
    }
}
