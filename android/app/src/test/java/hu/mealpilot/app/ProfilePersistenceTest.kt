package hu.mealpilot.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hu.mealpilot.app.data.prefs.SettingsRepository
import hu.mealpilot.core.model.ActivityLevel
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.DietStyle
import hu.mealpilot.core.model.MacroPreset
import hu.mealpilot.core.model.Sex
import hu.mealpilot.core.model.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A profil TÚLÉLI-e az app újraindítását — minden mezője.
 *
 * MIÉRT KELL. Egy új profilmező hozzáadása HÁROM helyet érint: az adatosztályt, a
 * mentést és a betöltést. Ha a mentés vagy a betöltés kimarad, a mező minden
 * indításkor csendben visszaáll az alapértelmezésre. Semmi nem jelez: a felület
 * beállítja, a felhasználó látja, hogy beállította, és másnap újra ott az alapérték.
 *
 * Eddig ezt semmi nem őrizte — a tegnap felvett „kétszer főzés" kapcsoló is
 * átmehetett volna hiányos bekötéssel.
 *
 * MIÉRT REFLEXIÓVAL. Egy kézzel felsorolt mezőlista pont arra a mezőre nem
 * figyelne, amit épp most vettek fel. A teszt ezért megköveteli, hogy a próbaprofil
 * MINDEN mezője eltérjen az alapértelmezéstől: egy új mező addig bukik, amíg valaki
 * be nem állítja itt is — és onnantól a kerekút-ellenőrzés magától lefedi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ProfilePersistenceTest {

    private lateinit var settings: SettingsRepository

    /** Minden mező szándékosan MÁS, mint az alapértelmezés. */
    private val filled = UserProfile(
        name = "Kovács Máté",
        sex = Sex.FEMALE,
        ageYears = 41,
        heightCm = 163.5,
        weightKg = 71.2,
        bodyFatPercent = 28.5,
        activityLevel = ActivityLevel.HIGH,
        targetWeightKg = 64.0,
        targetRateKgPerWeek = 0.75,
        dietStyle = DietStyle.PESCATARIAN,
        macroPreset = MacroPreset.LOW_CARB,
        mealsPerDay = 5,
        restrictions = setOf(DietRestriction.GLUTEN, DietRestriction.SESAME),
        preferences = "nem eszem gombát, hétköznap max 20 perc",
        batchCooking = true,
        mealTimes = listOf("06:45", "09:30", "13:15", "16:45", "20:15"),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settings = SettingsRepository(context)
    }

    @Test
    fun `the test profile differs from the defaults in every single field`() {
        // Ez az ŐR: enélkül egy elfelejtett mező azért menne át, mert a betöltés
        // alapértéke véletlenül megegyezik a próbaérték alapértékével.
        val defaults = UserProfile()
        val same = UserProfile::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .mapNotNull { field ->
                field.isAccessible = true
                field.name.takeIf { field.get(filled) == field.get(defaults) }
            }
        assertTrue(
            "Ezek a mezők az alapértelmezésen maradtak a próbaprofilban, tehát nem " +
                "bizonyítanak semmit: $same. Állítsd be őket másra.",
            same.isEmpty(),
        )
    }

    @Test
    fun `every field survives a save and a reload`() = runTest {
        settings.saveProfile(filled)
        assertEquals(filled, settings.currentProfile())
    }

    @Test
    fun `an unsaved profile comes back as the defaults, not as garbage`() = runTest {
        settings.clearAll()
        val loaded = settings.currentProfile()
        assertEquals(UserProfile(), loaded)
    }

    @Test
    fun `logging a weight updates the profile but keeps everything else`() = runTest {
        // A súlyrögzítés MELLÉKESEN írja a profilt. Könnyű elrontani úgy, hogy közben
        // a többi mezőt is felülcsapja az alapértékkel.
        settings.saveProfile(filled)
        settings.updateWeight(69.4, 27.0)

        val loaded = settings.currentProfile()
        assertEquals(69.4, loaded.weightKg, 0.001)
        assertEquals(27.0, loaded.bodyFatPercent!!, 0.001)
        assertEquals(filled.copy(weightKg = 69.4, bodyFatPercent = 27.0), loaded)
    }
}
