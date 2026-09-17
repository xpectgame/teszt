package hu.mealpilot.app

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import hu.mealpilot.app.i18n.LocalAppLanguage
import hu.mealpilot.app.ui.components.PROFILE_FIELD_WEIGHT
import hu.mealpilot.app.ui.components.ProfileForm
import hu.mealpilot.app.ui.theme.MealPilotTheme
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A profilűrlap érvényessége.
 *
 * A tartományon kívüli számot a mező NEM adja tovább: a profilban a régi érték marad.
 * Amíg ezt semmi nem jelezte a hívónak, a felhasználó a beírt számát látta a mezőben,
 * az app viszont a régivel számolt — a Beállításokban még egy „Profil elmentve."
 * üzenetet is kapott rá. Az egész kalóriakeret ezekből a számokból készül.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "hu-rHU-w390dp-h844dp-xhdpi")
class ProfileFormValidityTest {

    @get:Rule
    val compose = createComposeRule()

    private var profile = UserProfile(weightKg = 80.0)
    private var valid: Boolean? = null

    private fun form() {
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.HU) {
                MealPilotTheme(darkTheme = false) {
                    ProfileForm(
                        profile = profile,
                        onChange = { profile = it },
                        onValidityChange = { valid = it },
                    )
                }
            }
        }
    }

    @Test
    fun `az érintetlen űrlap érvényes`() {
        form()
        compose.waitForIdle()

        assertEquals(true, valid)
    }

    @Test
    fun `a tartományon kívüli súly érvénytelenné teszi az űrlapot`() {
        form()
        compose.onNodeWithTag(PROFILE_FIELD_WEIGHT).performTextClearance()
        compose.onNodeWithTag(PROFILE_FIELD_WEIGHT).performTextInput("310")
        compose.waitForIdle()

        assertEquals("az űrlapnak érvénytelennek kell lennie", false, valid)
        // A profil a RÉGI értéket hordozza — pont ezért nem szabad továbbengedni.
        assertEquals(80.0, profile.weightKg, 0.001)
    }

    @Test
    fun `a hibás mező kiírja a megengedett tartományt`() {
        // A piros keret megmondja, hogy baj van; azt nem, hogy mi.
        form()
        compose.onNodeWithTag(PROFILE_FIELD_WEIGHT).performTextClearance()
        compose.onNodeWithTag(PROFILE_FIELD_WEIGHT).performTextInput("310")
        compose.waitForIdle()

        compose.onNodeWithText("35 és 300 között.").assertIsDisplayed()
    }

    @Test
    fun `javítás után az űrlap újra érvényes`() {
        form()
        compose.onNodeWithTag(PROFILE_FIELD_WEIGHT).performTextClearance()
        compose.onNodeWithTag(PROFILE_FIELD_WEIGHT).performTextInput("310")
        compose.waitForIdle()
        assertFalse(valid!!)

        compose.onNodeWithTag(PROFILE_FIELD_WEIGHT).performTextClearance()
        compose.onNodeWithTag(PROFILE_FIELD_WEIGHT).performTextInput("95")
        compose.waitForIdle()

        assertTrue("javítás után érvényesnek kell lennie", valid!!)
        assertEquals(95.0, profile.weightKg, 0.001)
    }
}
