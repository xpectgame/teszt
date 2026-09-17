package hu.mealpilot.core

import hu.mealpilot.core.ai.ChatPrompts
import hu.mealpilot.core.ai.EstimatePrompts
import hu.mealpilot.core.ai.PlanPrompts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/**
 * A rendszerpromptok a backendben is megvannak (`backend/src/prompts.ts`), mert a
 * modell viselkedését a szervernek kell megszabnia, nem a telefonnak.
 *
 * Ez a teszt akkor bukik el, ha átírod a promptot, de az alábbi hasht nem. A javítás:
 *
 *     python3 backend/tools/gen-prompts.py      # a repó gyökeréből
 *
 * majd az alábbi hasheket írd át az újakra (a szkript kiírja őket).
 *
 * FONTOS, MIT NEM VÉD EZ A TESZT. A prompt egy BEMÁSOLT hashhez van hasonlítva, tehát
 * ha valaki csak az új hasht másolja ide, a szkript futtatása nélkül, ez a teszt is és
 * a backend tesztjei is zöldek maradnak — miközben a szerver a RÉGI promptot küldi a
 * modellnek. Ezt a rést a `gen-prompts.py --check` zárja, és a CI azt futtatja.
 */
class SystemPromptSyncTest {

    @Test
    fun `a tervező rendszerprompt egyezik a backendbe másolt változattal`() {
        assertEquals(
            "A PlanPrompts.SYSTEM megváltozott — futtasd a backend/tools/gen-prompts.py szkriptet.",
            "2b3561f227e3baf35c85138491b338e6e550dcbec5a8dedd544cd101a3fbcb8e",
            sha256(PlanPrompts.SYSTEM),
        )
    }

    @Test
    fun `a beszélgető rendszerprompt egyezik a backendbe másolt változattal`() {
        assertEquals(
            "A ChatPrompts.SYSTEM megváltozott — futtasd a backend/tools/gen-prompts.py szkriptet.",
            "73035df0000a0a46ffcec379be35ff49376dc49559539c3251e3d135c895361d",
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
            "b3e8b223dc88c85a3ba0654a006429951bb0979b07bdad8c59adfcb285a6dfcf",
            sha256(ChatPrompts.SYSTEM_EN),
        )
    }

    @Test
    fun `a becslő rendszerprompt egyezik a backendbe másolt változattal`() {
        assertEquals(
            "Az EstimatePrompts.SYSTEM megváltozott — futtasd a backend/tools/gen-prompts.py szkriptet.",
            "443769ed2f44c08fb1ca208f8607a66859e3bed11f615f60fb30b4a575be51be",
            sha256(EstimatePrompts.SYSTEM),
        )
    }

    @Test
    fun `az angol becslő rendszerprompt egyezik a backendbe másolt változattal`() {
        assertEquals(
            "Az EstimatePrompts.SYSTEM_EN megváltozott — futtasd a backend/tools/gen-prompts.py szkriptet.",
            "933c185ac1b9bae7895323049c68ccad104ad19ee53e48a04da002cef6edba7b",
            sha256(EstimatePrompts.SYSTEM_EN),
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
