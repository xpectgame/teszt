package hu.mealpilot.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Egy üzenet a beszélgetésben. */
data class ChatTurn(val role: Role, val text: String) {
    enum class Role { USER, ASSISTANT }
}

/**
 * Amit a beszélgetőtárs tud a felhasználóról. Szándékosan sima adat, hogy a :core
 * modul ne függjön az adatbázistól — a repository tölti ki.
 */
data class ChatContext(
    val profileSummary: String,
    val targetSummary: String,
    val planSummary: String,
    val todaySummary: String,
    val recentProgress: String,
    val restrictions: List<String>,
    val availableDayCount: Int,
)

/**
 * A beszélgetés során kérhető művelet.
 *
 * A modell nem hajtja végre ezeket — csak megnevezi, mit szeretne a felhasználó,
 * és az app dönti el, hogy végrehajtja-e. Így egy félreértett kérés nem ír át
 * csendben egy egész hónapot.
 */
enum class ChatActionType {
    /** Nincs teendő, csak válasz. */
    NONE,

    /** A teljes aktív terv újratervezése az `instruction` alapján. */
    REGENERATE_PLAN,

    /** Adott napok újratervezése (`day_indexes`). */
    REGENERATE_DAYS,

    /** Új terv készítése `days` hosszan. */
    CREATE_PLAN,

    /** Étrendi kizárások hozzáadása. */
    ADD_RESTRICTIONS,

    /** Az állandó preferenciák szövegének módosítása. */
    SET_PREFERENCES,

    /** A fogyás ütemének módosítása. */
    ADJUST_RATE,

    /** Súly rögzítése. */
    LOG_WEIGHT;

    companion object {
        fun fromRaw(raw: String?): ChatActionType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NONE
    }
}

@Serializable
data class AiChatAction(
    val type: String = "NONE",
    @SerialName("day_indexes") val dayIndexes: List<Int> = emptyList(),
    /** Amit a tervezőnek át kell adni — magyarul, konkrétan. */
    val instruction: String = "",
    val days: Int = 0,
    val restrictions: List<String> = emptyList(),
    val preferences: String = "",
    @SerialName("rate_kg_per_week") val rateKgPerWeek: Double = 0.0,
    @SerialName("weight_kg") val weightKg: Double = 0.0,
    /**
     * Egy mondat arról, mit fog az app csinálni — ezt mutatjuk meg megerősítésre,
     * mielőtt bármi visszafordíthatatlan történne.
     */
    @SerialName("confirm_label") val confirmLabel: String = "",
) {
    val actionType: ChatActionType get() = ChatActionType.fromRaw(type)
}

@Serializable
data class AiChatResponse(
    val reply: String = "",
    val action: AiChatAction = AiChatAction(),
)
