package hu.mealpilot.app

import hu.mealpilot.app.data.repo.ReportKind
import hu.mealpilot.app.data.repo.ReportOutcome
import hu.mealpilot.app.data.repo.ReportReason
import hu.mealpilot.app.data.repo.ReportRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A bejelentés e-mailes tartaléka.
 *
 * Ez az út akkor lép be, amikor a szolgáltatás nem elérhető — vagyis pont akkor,
 * amikor a bejelentés a legfontosabb. Ha ez a levél olvashatatlan, a Play által
 * elvárt visszajelzési csatorna papíron létezik csak.
 */
class ReportEmailTest {

    private fun email(
        reason: ReportReason = ReportReason.HARMFUL,
        detail: String = "",
        payload: String? = null,
    ): ReportOutcome.UseEmail = runBlocking {
        val outcome = ReportRepository(backend = null)
            .submit(ReportKind.PLAN, reason, detail, payload)
        assertTrue("backend nélkül e-mailre kell esnie", outcome is ReportOutcome.UseEmail)
        outcome as ReportOutcome.UseEmail
    }

    @Test
    fun `az ok szövegként kerül a levélbe, nem eroforras-azonositokent`() {
        // A `label` egy R.string azonosító. Behelyettesítve egy nyolcjegyű szám lett
        // belőle: „Ok: 2131886234" — ebből senki nem tudja, mit kifogásolt a felhasználó.
        val body = email(reason = ReportReason.IGNORED_RESTRICTION).body

        assertTrue("az ok hiányzik a levélből: $body", body.contains("Ok: IGNORED_RESTRICTION"))
        assertTrue(
            "erőforrás-azonosító került a levélbe: $body",
            Regex("Ok: \\d+").containsMatchIn(body).not(),
        )
    }

    @Test
    fun `minden ok kódja olvasható marad`() {
        ReportReason.entries.forEach { reason ->
            val body = email(reason = reason).body
            assertTrue("$reason: $body", body.contains("Ok: ${reason.code}"))
        }
    }

    @Test
    fun `a kifogásolt tartalom levágva, de benne van`() {
        val outcome = email(reason = ReportReason.WRONG_NUTRITION, detail = "A kalória duplája", payload = "x".repeat(5_000))

        assertEquals("MealPilot report — WRONG_NUTRITION", outcome.subject)
        assertTrue(outcome.body.contains("A kalória duplája"))
        // A levéltest nem nőhet akármeddig, de a tartalomnak benne kell lennie.
        assertTrue(outcome.body.contains("--- reported content ---"))
        assertTrue(outcome.body.length < 4_000)
    }
}
