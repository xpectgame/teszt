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
            "89d22742a3a6fbcdf9e923f8a2eaa8290a0138b89ffa5b95fa4291d6886faa08",
            sha256(PlanPrompts.SYSTEM),
        )
    }

    @Test
    fun `a beszélgető rendszerprompt egyezik a backendbe másolt változattal`() {
        assertEquals(
            "A ChatPrompts.SYSTEM megváltozott — futtasd a backend/tools/gen-prompts.py szkriptet.",
            "710f3f17cb152c87b71513e6fde2da81cf48c5fd7e0cdc98cbd95fdf39e4bf23",
            sha256(ChatPrompts.SYSTEM),
        )
    }

    @Test
    fun `az angol tervező rendszerprompt egyezik a backendbe másolt változattal`() {
        assertEquals(
            "A PlanPrompts.SYSTEM_EN megváltozott — futtasd a backend/tools/gen-prompts.py szkriptet.",
            "127de26af7626a484376242a51e389defed75ecad1fbf473ffd3cc51a7c0e1f6",
            sha256(PlanPrompts.SYSTEM_EN),
        )
    }

    @Test
    fun `az angol beszélgető rendszerprompt egyezik a backendbe másolt változattal`() {
        assertEquals(
            "A ChatPrompts.SYSTEM_EN megváltozott — futtasd a backend/tools/gen-prompts.py szkriptet.",
            "4be35abccd9fd76d1cee4cd66b2a3915a8a517d5f7fb18a61dfe1793037abc00",
            sha256(ChatPrompts.SYSTEM_EN),
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
