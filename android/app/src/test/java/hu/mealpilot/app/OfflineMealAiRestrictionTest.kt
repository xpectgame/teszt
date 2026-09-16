package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.ai.OfflineMealAi
import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.core.ai.PlanRequest
import hu.mealpilot.core.ai.RestrictionChecker
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
 * A beépített, sablonos tervező és a kizárások.
 *
 * Ez a tervező akkor lép működésbe, ha a modellhívás félbeszakad — hálózat, kvóta,
 * szolgáltatáshiba —, és amit kiad, az EGYENESEN a felhasználó tervébe kerül: erre az
 * útra nem fut rá a modellválaszt vizsgáló ellenőrzés, mert nincs kit megkérni a
 * javításra.
 *
 * Korábban a fogásokat kizárólag a nap és a fogás sorszámából választotta ki, a profilt
 * meg sem nézte. A sablonok között van „teljes kiőrlésű kenyér" és „görög joghurt
 * dióval" — vagyis egy gluténérzékeny vagy mogyoróallergiás felhasználó pontosan azt
 * kapta volna, amit a saját profiljában kizárt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OfflineMealAiRestrictionTest {

    private fun planner(): OfflineMealAi {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return OfflineMealAi(
            languageProvider = { AppLanguage.HU },
            strings = AppStrings(context) { AppLanguage.HU },
        )
    }

    private fun request(restrictions: Set<DietRestriction>, days: Int = 7): PlanRequest {
        val profile = UserProfile(restrictions = restrictions)
        return PlanRequest(
            profile = profile,
            budget = EnergyCalculator.budget(profile, AppLanguage.HU),
            days = days,
            startDayIndex = 0,
            totalDays = days,
        )
    }

    @Test
    fun `the built-in planner never serves an excluded ingredient`() = runTest {
        // Több kizárás egyszerre, egy egész hétre: minden nap minden fogását nézzük.
        val restrictions = setOf(DietRestriction.TREE_NUT, DietRestriction.GLUTEN)
        val plan = planner().generatePlan(request(restrictions)).getOrThrow()

        val unsafe = plan.days.flatMap { it.meals }
            .filterNot { RestrictionChecker.isSafe(it, restrictions, AppLanguage.HU) }

        assertTrue(
            "Kizárt hozzávaló került a tervbe: ${unsafe.map { it.name }}",
            unsafe.isEmpty(),
        )
        assertTrue("A terv ne legyen üres", plan.days.flatMap { it.meals }.isNotEmpty())
    }

    @Test
    fun `without exclusions the planner still fills every day`() = runTest {
        // A szűrés nem veheti el a működő esetet: kizárás nélkül minden napra jut fogás.
        val plan = planner().generatePlan(request(emptySet())).getOrThrow()
        assertEquals(7, plan.days.size)
        assertTrue(plan.days.all { it.meals.isNotEmpty() })
    }
}
