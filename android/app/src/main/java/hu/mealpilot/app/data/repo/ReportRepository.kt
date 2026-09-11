package hu.mealpilot.app.data.repo

import hu.mealpilot.app.data.remote.BackendClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Mire vonatkozik a bejelentés. */
enum class ReportKind { PLAN, MEAL, CHAT }

/**
 * Miért jelenti a felhasználó.
 *
 * A lista szándékosan rövid és konkrét: a szabad szöveges bejelentésekből nehéz
 * mintázatot látni, ezekből viszont azonnal kiderül, ha egy hibatípus elszaporodik.
 * A „veszélyes vagy sértő" kategória külön van, mert azt nem statisztikázni kell,
 * hanem megnézni.
 */
enum class ReportReason(val code: String, val label: String) {
    WRONG_NUTRITION("WRONG_NUTRITION", "A tápértékadatok nem stimmelnek"),
    IGNORED_RESTRICTION("IGNORED_RESTRICTION", "Olyat ajánlott, amit kizártam"),
    UNREALISTIC("UNREALISTIC", "Értelmetlen vagy megfőzhetetlen fogás"),
    HARMFUL("HARMFUL", "Veszélyes, sértő vagy egészségügyi tanácsnak tűnő tartalom"),
    OTHER("OTHER", "Egyéb"),
}

/** A bejelentés sorsa — a felület ebből tudja, mit mondjon a felhasználónak. */
sealed interface ReportOutcome {
    /** Elküldve a szolgáltatásnak. */
    data object Sent : ReportOutcome

    /**
     * Nincs hova küldeni (nincs backend a buildben), vagy nem sikerült.
     * A felület ilyenkor e-mailt nyit az előre kitöltött szöveggel — a visszajelzési
     * útnak akkor is működnie kell, ha a szolgáltatás éppen nem elérhető.
     */
    data class UseEmail(val subject: String, val body: String) : ReportOutcome
}

/**
 * „Jelentsd ezt a tervet".
 *
 * A Play a generatív AI funkcióknál elvárja, hogy a felhasználó jelenteni tudja a
 * problémás tartalmat. Gyakorlati haszna is van: ez az egyetlen jelzés arról, ha a
 * tervező rendszeresen hibázik valamiben.
 */
class ReportRepository(private val backend: BackendClient?) {

    val canSubmitOnline: Boolean get() = backend?.isConfigured == true

    suspend fun submit(
        kind: ReportKind,
        reason: ReportReason,
        detail: String,
        /** A kifogásolt tartalom, hogy a bejelentés önmagában értelmezhető legyen. */
        payload: String?,
    ): ReportOutcome = withContext(Dispatchers.IO) {
        val client = backend
        if (client == null || !client.isConfigured) return@withContext email(kind, reason, detail, payload)

        runCatching {
            client.report(
                kind = kind.name,
                reason = reason.code,
                detail = detail.takeIf { it.isNotBlank() },
                payload = payload,
            )
        }.fold(
            onSuccess = { ReportOutcome.Sent },
            onFailure = { email(kind, reason, detail, payload) },
        )
    }

    private fun email(
        kind: ReportKind,
        reason: ReportReason,
        detail: String,
        payload: String?,
    ): ReportOutcome.UseEmail = ReportOutcome.UseEmail(
        subject = "MealPilot bejelentés — ${reason.label}",
        body = buildString {
            appendLine("Mire vonatkozik: ${kind.name}")
            appendLine("Ok: ${reason.label}")
            if (detail.isNotBlank()) {
                appendLine()
                appendLine(detail)
            }
            if (!payload.isNullOrBlank()) {
                appendLine()
                appendLine("--- a kifogásolt tartalom ---")
                appendLine(payload.take(MAX_EMAIL_PAYLOAD))
            }
        },
    )

    private companion object {
        /** Egy levéltest nem lehet akármekkora, és a lényeg úgyis az első pár fogás. */
        const val MAX_EMAIL_PAYLOAD = 3_000
    }
}
