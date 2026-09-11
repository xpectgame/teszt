package hu.mealpilot.core

import hu.mealpilot.core.ai.ChatPrompts
import hu.mealpilot.core.ai.PlanPrompts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/**
 * A rendszerpromptok a backendben is megvannak (`backend/src/prompts.ts`), mert a
 * modell viselkedését a szervernek kell megszabnia, nem a telefonnak.
 *
 * Ez a teszt akkor bukik el, ha itt átírod a promptot, de a backendben nem: a
 * felhasználó ilyenkor mást kapna, mint amit a fejlesztő gondol. A javítás:
 *
 *     python3 backend/tools/gen-prompts.py      # a repó gyökeréből
 *
 * majd az alábbi hasheket írd át az újakra (a szkript kiírja őket).
 */
class SystemPromptSyncTest {

    @Test
    fun `a tervező rendszerprompt egyezik a backendbe másolt változattal`() {
        assertEquals(
            "A PlanPrompts.SYSTEM megváltozott — futtasd a backend/tools/gen-prompts.py szkriptet.",
            "27f4d91211ecd5653e83a72013cbc7c2726ad255e3928f5bf619a3ad0c98bfc6",
            sha256(PlanPrompts.SYSTEM),
        )
    }

    @Test
    fun `a beszélgető rendszerprompt egyezik a backendbe másolt változattal`() {
        assertEquals(
            "A ChatPrompts.SYSTEM megváltozott — futtasd a backend/tools/gen-prompts.py szkriptet.",
            "48111e46528b6f1f1f6bc4ed9ef519b5325ceb5aad575399848122a21a670452",
            sha256(ChatPrompts.SYSTEM),
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
