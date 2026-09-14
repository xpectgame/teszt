package hu.mealpilot.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import hu.mealpilot.app.ui.components.MealEntryForm
import hu.mealpilot.app.ui.components.TAG_DESCRIPTION
import hu.mealpilot.app.ui.theme.MealPilotTheme
import hu.mealpilot.core.ai.AiMealEstimate
import hu.mealpilot.core.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A „mást ettem" űrlap.
 *
 * Ez az app legsűrűbben használt művelete: a naplózás. Amíg a négy számot kézzel
 * kellett beírni, a szöveges becslés volt a hiányzó lépés — most ezt is ez az űrlap
 * végzi, tehát itt a legdrágább, ha elromlik.
 */
@RunWith(RobolectricTestRunner::class)
// Magyar nyelven: az app elsődleges piaca ez, és a szövegek a values-hu-ból
// jönnek — a teszt így azt is bizonyítja, hogy a magyar erőforrások be vannak kötve.
@Config(application = Application::class, qualifiers = "hu-rHU-w390dp-h844dp-xhdpi")
class MealEntryFormTest {

    @get:Rule
    val compose = createComposeRule()

    private var saved: Pair<String, Nutrients>? = null

    private fun form(
        initialName: String = "",
        initialNutrients: Nutrients? = null,
        onEstimate: (suspend (String) -> Result<AiMealEstimate>)? = null,
    ) {
        compose.setContent {
            MealPilotTheme(darkTheme = false) {
                MealEntryForm(
                    title = "Mást ettem",
                    initialName = initialName,
                    initialNutrients = initialNutrients,
                    confirmLabel = "Ezt ettem",
                    onDismiss = {},
                    onSave = { name, nutrients -> saved = name to nutrients },
                    onEstimate = onEstimate,
                )
            }
        }
    }

    @Test
    fun `becslő nélkül a szöveges mező meg sem jelenik`() {
        // Egy sosem működő gomb rosszabb, mint a hiányzó funkció: kulcs vagy backend
        // nélkül a becslés nem tud lefutni, tehát nem is kínáljuk fel.
        form(onEstimate = null)

        compose.onNodeWithTag(TAG_DESCRIPTION).assertDoesNotExist()
        compose.onNodeWithText("Számold ki").assertDoesNotExist()
    }

    @Test
    fun `a becslés kitölti a mezőket`() {
        form(
            onEstimate = {
                Result.success(
                    AiMealEstimate(
                        name = "Gyrosos pita",
                        kcal = 720.0,
                        proteinG = 34.0,
                        carbsG = 78.0,
                        fatG = 30.0,
                        assumption = "Egy közepes adaggal számolva.",
                    )
                )
            }
        )

        compose.onNodeWithTag(TAG_DESCRIPTION).performTextInput("egy gyros pita")
        compose.onNodeWithText("Számold ki").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Gyrosos pita").assertIsDisplayed()
        compose.onNodeWithText("720").assertIsDisplayed()
        // A feltételezés nem dísz: ebből derül ki, ha egy adaggal számolt, a
        // felhasználó viszont kettőt evett.
        compose.onNodeWithText("Egy közepes adaggal számolva.").assertIsDisplayed()
    }

    @Test
    fun `a sikertelen becslés megmondja, mi történt`() {
        form(onEstimate = { Result.failure(IllegalStateException("Ezt nem sikerült kiszámolnom.")) })

        compose.onNodeWithTag(TAG_DESCRIPTION).performTextInput("öhm")
        compose.onNodeWithText("Számold ki").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Ezt nem sikerült kiszámolnom.").assertIsDisplayed()
        // Kudarc után is lehessen kézzel beírni: a mezők nem tűnnek el.
        compose.onNodeWithText("Mit ettél?").assertIsDisplayed()
    }

    @Test
    fun `üres leírással nem indul becslés`() {
        var called = false
        form(onEstimate = { called = true; Result.success(AiMealEstimate()) })

        compose.onNodeWithText("Számold ki").assertIsNotEnabled()
        assertEquals(false, called)
    }

    @Test
    fun `kalória nélkül nem lehet menteni`() {
        // A név önmagában semmit nem ér a naplóban: a keret a kalóriából áll össze.
        form(initialName = "Valami")

        compose.onNodeWithText("Ezt ettem").assertIsNotEnabled()
        assertNull(saved)
    }

    @Test
    fun `a mentés a beírt értékeket adja tovább`() {
        form(initialName = "Zabkása", initialNutrients = Nutrients(kcal = 420.0, proteinG = 14.0))

        compose.onNodeWithText("Ezt ettem").assertIsEnabled()
        compose.onNodeWithText("Ezt ettem").performClick()
        compose.waitForIdle()

        assertEquals("Zabkása", saved?.first)
        assertEquals(420.0, saved?.second?.kcal ?: 0.0, 0.001)
        assertEquals(14.0, saved?.second?.proteinG ?: 0.0, 0.001)
    }
}
