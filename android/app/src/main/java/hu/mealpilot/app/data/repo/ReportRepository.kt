package hu.mealpilot.app.data.repo

import androidx.annotation.StringRes
import hu.mealpilot.app.R
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
enum class ReportReason(val code: String, @StringRes val label: Int) {
    WRONG_NUTRITION("WRONG_NUTRITION", R.string.report_reason_nutrition),
    IGNORED_RESTRICTION("IGNORED_RESTRICTION", R.string.report_reason_restriction),
    UNREALISTIC("UNREALISTIC", R.string.report_reason_unrealistic),
    HARMFUL("HARMFUL", R.string.report_reason_harmful),
    OTHER("OTHER", R.string.report_reason_other),
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
        subject = "MealPilot report — ${reason.code}",
        body = buildString {
            appendLine("Mire vonatkozik: ${kind.name}")
            // A `label` egy erőforrás-azonosító, nem szöveg: kiírva egy nyolcjegyű
            // szám lett belőle a levélben. A `code` az, ami olvasható — és ez az
            // egyetlen út, ami akkor is működik, ha a szolgáltatás nem elérhető.
            appendLine("Ok: ${reason.code}")
            if (detail.isNotBlank()) {
                appendLine()
                appendLine(detail)
            }
            if (!payload.isNullOrBlank()) {
                appendLine()
                appendLine("--- reported content ---")
                appendLine(payload.take(MAX_EMAIL_PAYLOAD))
            }
        },
    )

    private companion object {
        /** Egy levéltest nem lehet akármekkora, és a lényeg úgyis az első pár fogás. */
        const val MAX_EMAIL_PAYLOAD = 3_000
    }
}
