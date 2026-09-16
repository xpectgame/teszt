package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.ai.StreamingMealAi
import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.core.ai.PlanRequest
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Egy nap átírása és a kizárások.
 *
 * A teljes terv útján van kizárás-ellenőrzés és javító kör. Egy nap átírásán nem volt
 * SEMMI: a modell válasza egyenesen a tervbe került. A prompt kéri a kizárásokat, de a
 * kérés nem garancia — ezt az app máshol maga is kimondja.
 *
 * Ez nem elméleti út: a beszélgetésből („írd át a mai vacsorát") pontosan ide fut a
 * vezérlés, és ez fizetős funkció, tehát használni is fogják.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RefineDayRestrictionTest {

    /** Tervező, ami előre megírt nyers válaszokat ad vissza, hívásonként egyet. */
    private class ScriptedAi(
        strings: AppStrings,
        private val answers: List<String>,
    ) : StreamingMealAi(strings, { AppLanguage.HU }) {
        var calls = 0
            private set

        override val isConfigured = true

        override suspend fun call(
            task: AiTask,
            userText: String,
            planDays: Int,
            chunkIndex: Int,
            isRetry: Boolean,
            onChars: (Int) -> Unit,
        ): String = answers[minOf(calls++, answers.lastIndex)]

        /** A tesztben nincs mit fordítani: a hiba önmagát képviseli. */
        override fun translate(error: Throwable): Throwable = error
    }

    private fun strings(): AppStrings {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AppStrings(context) { AppLanguage.HU }
    }

    private fun request(restrictions: Set<DietRestriction>): PlanRequest {
        val profile = UserProfile(restrictions = restrictions)
        return PlanRequest(
            profile = profile,
            budget = EnergyCalculator.budget(profile, AppLanguage.HU),
            days = 1,
            startDayIndex = 0,
            totalDays = 1,
        )
    }

    /** Egy nap válasza nyers JSON-ként, a megadott hozzávalóval. */
    private fun dayJson(mealName: String, ingredient: String) = """
        {"day_index": 0, "explanation": "Kész.", "day": {"day_index": 0, "meals": [
          {"slot": "DINNER", "time": "19:00", "name": "$mealName",
           "ingredients": [{"name": "$ingredient", "quantity": 100, "unit": "g", "aisle": "EGYEB"}],
           "nutrition": {"kcal": 500, "protein_g": 30, "carbs_g": 40, "fat_g": 20}}
        ]}}
    """.trimIndent()

    @Test
    fun `an excluded ingredient is sent back for repair, not saved`() = runTest {
        val ai = ScriptedAi(
            strings(),
            listOf(
                dayJson("Diós saláta", "dió"),          // ütközik a mogyoróallergiával
                dayJson("Csirkés saláta", "csirkemell"), // a javító kör rendes választ ad
            ),
        )

        val result = ai.refineDay(request(setOf(DietRestriction.TREE_NUT)), "{}", "könnyebbet")

        assertTrue("A javító kör után sikeresnek kell lennie", result.isSuccess)
        assertEquals("Pontosan egy javító kör fusson", 2, ai.calls)
        assertEquals("Csirkés saláta", result.getOrThrow().day.meals.single().name)
    }

    @Test
    fun `if the repair round still clashes the day is refused`() = runTest {
        // Inkább maradjon a régi nap, mint hogy allergén kerüljön a tervbe.
        val ai = ScriptedAi(strings(), listOf(dayJson("Diós saláta", "dió")))

        val result = ai.refineDay(request(setOf(DietRestriction.TREE_NUT)), "{}", "könnyebbet")

        assertTrue("Nem szabad elfogadni", result.isFailure)
        assertEquals("Egy próbálkozás és egy javító kör, több nem", 2, ai.calls)
    }

    @Test
    fun `without exclusions nothing changes - one call, accepted`() = runTest {
        // A szűrés nem vehet el a működő esetből: kizárás nélkül egyetlen hívás van.
        val ai = ScriptedAi(strings(), listOf(dayJson("Diós saláta", "dió")))

        val result = ai.refineDay(request(emptySet()), "{}", "könnyebbet")

        assertTrue(result.isSuccess)
        assertEquals("Fölösleges kör nem futhat", 1, ai.calls)
    }
}
