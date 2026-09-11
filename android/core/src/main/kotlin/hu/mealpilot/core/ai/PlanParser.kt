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

    private fun String.removeCodeFence(): String {
        if (!startsWith("```")) return this
        return substringAfter('\n', "").substringBeforeLast("```").trim()
    }
}
