package hu.mealpilot.app

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.local.MealEntity
import hu.mealpilot.app.data.local.MealLogEntity
import hu.mealpilot.app.data.local.MealWithIngredients
import hu.mealpilot.app.data.local.NutrientsColumns
import hu.mealpilot.app.i18n.LocalAppLanguage
import hu.mealpilot.app.ui.screens.MealRow
import hu.mealpilot.app.ui.theme.MealPilotTheme
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.DietRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A nap étkezéslapkája — az app legtöbbet nézett eleme.
 *
 * A „Ma" képernyőn naponta többször látja a felhasználó, és innen indul a naplózás.
 * A lapka három állapotot tud: soron lévő (gombokkal), megevett és kihagyott.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "hu-rHU-w390dp-h844dp-xhdpi")
class MealRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val lunch = MealWithIngredients(
        meal = MealEntity(
            id = 1,
            planId = 1,
            dayIndex = 0,
            epochDay = 20_000,
            slot = "LUNCH",
            timeText = "12:30",
            scheduledAtMillis = 0,
            name = "Grillcsirke barna rizzsel",
            nutrients = NutrientsColumns(kcal = 610.0, proteinG = 52.0, carbsG = 62.0, fatG = 16.0),
        ),
        ingredients = emptyList(),
    )

    private fun row(
        status: LogStatus? = null,
        log: MealLogEntity? = null,
        onAte: () -> Unit = {},
        onSkip: () -> Unit = {},
        onOpen: () -> Unit = {},
        onUndo: () -> Unit = {},
        meal: MealWithIngredients = lunch,
        violations: List<DietRestriction> = emptyList(),
        isFavorite: Boolean = false,
        onToggleFavorite: () -> Unit = {},
        onSwap: () -> Unit = {},
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.HU) {
                MealPilotTheme(darkTheme = false) {
                    MealRow(
                        meal = meal,
                        log = log,
                        status = status,
                        violations = violations,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onSwap = onSwap,
                        onOpen = onOpen,
                        onAte = onAte,
                        onSkip = onSkip,
                        onReplace = {},
                        onUndo = onUndo,
                    )
                }
            }
        }
    }

    @Test
    fun `a soron lévő étkezés a nevét, az idejét és a tápértékét mutatja`() {
        row()

        compose.onNodeWithText("Grillcsirke barna rizzsel").assertIsDisplayed()
        compose.onNodeWithText("12:30").assertIsDisplayed()
        compose.onNodeWithText("EBÉD").assertIsDisplayed()
        compose.onNodeWithText("610 kcal · F 52 g · Sz 62 g · Zs 16 g").assertIsDisplayed()
    }

    @Test
    fun `a soron lévő étkezésnél ott a két gomb`() {
        // A naplózás egy koppintás legyen. Ha ez a gombpár eltűnik, az app napi
        // alapművelete kerül két koppintással messzebb.
        row()

        compose.onNodeWithText("Megettem").assertIsDisplayed()
        compose.onNodeWithText("Kihagytam").assertIsDisplayed()
    }

    @Test
    fun `a gombok a hozzájuk tartozó műveletet hívják`() {
        var ate = 0
        var skipped = 0
        row(onAte = { ate++ }, onSkip = { skipped++ })

        compose.onNodeWithText("Megettem").performClick()
        compose.waitForIdle()
        assertEquals(1, ate)
        assertEquals(0, skipped)

        compose.onNodeWithText("Kihagytam").performClick()
        compose.waitForIdle()
        assertEquals(1, skipped)
    }

    @Test
    fun `a lezárt étkezésnél a gombpár helyét az állapot veszi át`() {
        row(status = LogStatus.EATEN)

        compose.onNodeWithText("Megetted").assertIsDisplayed()
        compose.onNodeWithText("Megettem").assertDoesNotExist()
        compose.onNodeWithText("Kihagytam").assertDoesNotExist()
        compose.onNodeWithText("Vissza").assertIsDisplayed()
    }

    @Test
    fun `a kihagyott étkezés kihagyottnak látszik`() {
        row(status = LogStatus.SKIPPED)

        compose.onNodeWithText("Kihagytad").assertIsDisplayed()
        compose.onNodeWithText("Grillcsirke barna rizzsel").assertIsDisplayed()
    }

    @Test
    fun `felülírásnál azt mutatja, amit tényleg megevett`() {
        // Nem azt, amit terveztünk. Különben a napló arról szólna, mi lett volna a
        // terv, nem arról, mi történt.
        row(
            status = LogStatus.REPLACED,
            log = MealLogEntity(
                mealId = 1,
                epochDay = 20_000,
                loggedAtMillis = 0,
                status = LogStatus.REPLACED.name,
                name = "Gyrosos pita",
                nutrients = NutrientsColumns(kcal = 720.0, proteinG = 34.0, carbsG = 78.0, fatG = 30.0),
            ),
        )

        compose.onNodeWithText("Gyrosos pita").assertIsDisplayed()
        compose.onNodeWithText("Grillcsirke barna rizzsel").assertDoesNotExist()
        compose.onNodeWithText("720 kcal · F 34 g · Sz 78 g · Zs 30 g").assertIsDisplayed()
        compose.onNodeWithText("Mást ettél").assertIsDisplayed()
    }

    @Test
    fun `a lapkára koppintva megnyílik a részletek`() {
        var opened = false
        row(onOpen = { opened = true })

        compose.onNodeWithText("Grillcsirke barna rizzsel").performClick()
        compose.waitForIdle()
        assertTrue(opened)
    }

    /**
     * A tervező csak azokat a kizárásokat szűrte ki, amik a terv KÉSZÍTÉSEKOR éltek.
     * Aki ma jelenti be a mogyoróallergiáját, annak a tegnapi tervében ott marad a
     * mogyoróvaj — és eddig semmi nem szólt róla. Ez a lapka az utolsó hely, ahol
     * szólni lehet: innen indul a „Megettem".
     */
    @Test
    fun `az utólag felvett kizárásról a lapka szól`() {
        row(violations = listOf(DietRestriction.PEANUT))

        compose.onNodeWithText(
            "Ez a fogás ütközik egy utólag felvett kizárásoddal: Földimogyoró. " +
                "Írasd át a napot, vagy egyél mást.",
        ).assertIsDisplayed()
    }

    @Test
    fun `kizárás nélkül nincs figyelmeztetés`() {
        row()

        compose.onNodeWithText(
            "Ez a fogás ütközik egy utólag felvett kizárásoddal: Földimogyoró. " +
                "Írasd át a napot, vagy egyél mást.",
        ).assertDoesNotExist()
    }

    @Test
    fun `a már megevett fogásnál is kint marad a figyelmeztetés`() {
        // Ha kiderül, hogy allergént evett, azt utólag is tudnia kell.
        row(status = LogStatus.EATEN, violations = listOf(DietRestriction.PEANUT))

        compose.onNodeWithText(
            "Ez a fogás ütközik egy utólag felvett kizárásoddal: Földimogyoró. " +
                "Írasd át a napot, vagy egyél mást.",
        ).assertIsDisplayed()
    }
}
