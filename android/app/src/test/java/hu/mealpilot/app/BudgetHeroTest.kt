package hu.mealpilot.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import hu.mealpilot.app.ui.components.BudgetHero
import hu.mealpilot.app.ui.components.HeroLabels
import hu.mealpilot.app.ui.components.MacroLine
import hu.mealpilot.app.ui.theme.MealPilotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A „Ma" képernyő hős doboza.
 *
 * Ez a mai elrendezési hiba regressziós tesztje. A makróhasábnak nem volt súlya, ezért
 * a teljes szélességet elfoglalta; a nagy számnak nulla hely maradt, és láthatatlanul,
 * soronként egy karakterrel tördelve nyúlt le a képernyő aljáig. A kód lefordult, a
 * lint átment — csak épp a legfontosabb szám nem látszott.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
class BudgetHeroTest {

    @get:Rule
    val compose = createComposeRule()

    private fun labels(macroNames: List<String>) = HeroLabels(
        lines = macroNames.map { MacroLine(it, 96.0, 140) },
        leftLabel = "kcal fér még bele ma",
        overLabel = "kcal-lal a kereted fölött",
        totalLabel = "1 240 / 1 980 kcal",
    )

    @Test
    fun `a nagy szám látszik`() {
        compose.setContent {
            MealPilotTheme(darkTheme = false) {
                BudgetHero(
                    consumedKcal = 1_240,
                    targetKcal = 1_980,
                    labels = labels(listOf("Fehérje", "Szénhidrát", "Zsír", "Rost")),
                )
            }
        }

        compose.onNodeWithText("740").assertIsDisplayed()
        compose.onNodeWithText("kcal fér még bele ma").assertIsDisplayed()
    }

    @Test
    fun `hosszú makrónevek sem szorítják ki a nagy számot`() {
        // Pontosan ez volt a hiba természete: a jobb hasáb annyit vett el, amennyit
        // akart. Hosszú feliratokkal — például angolul, vagy nagy rendszerbetűvel —
        // ez azonnal előjön.
        compose.setContent {
            MealPilotTheme(darkTheme = false) {
                BudgetHero(
                    consumedKcal = 1_240,
                    targetKcal = 1_980,
                    labels = labels(
                        listOf("Szénhidrát összesen", "Telített zsírsav", "Élelmi rost", "Fehérje")
                    ),
                )
            }
        }

        compose.onNodeWithText("740").assertIsDisplayed()
    }

    @Test
    fun `a keret túllépése az eltérést mutatja, nem negatív számot`() {
        compose.setContent {
            MealPilotTheme(darkTheme = false) {
                BudgetHero(
                    consumedKcal = 2_200,
                    targetKcal = 1_980,
                    labels = labels(listOf("Fehérje", "Szénhidrát", "Zsír")),
                )
            }
        }

        compose.onNodeWithText("220").assertIsDisplayed()
        compose.onNodeWithText("kcal-lal a kereted fölött").assertIsDisplayed()
    }

    @Test
    fun `sötét témában is látszik a szám`() {
        compose.setContent {
            MealPilotTheme(darkTheme = true) {
                BudgetHero(
                    consumedKcal = 0,
                    targetKcal = 1_735,
                    labels = labels(listOf("Fehérje", "Szénhidrát", "Zsír", "Rost")),
                )
            }
        }

        compose.onNodeWithText("1735").assertIsDisplayed()
    }
}
