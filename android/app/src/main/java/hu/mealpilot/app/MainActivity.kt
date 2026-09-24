package hu.mealpilot.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import hu.mealpilot.app.i18n.LanguageStore
import hu.mealpilot.app.ui.AppRoot
import hu.mealpilot.app.ui.theme.MealPilotTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingMealId by mutableStateOf<Long?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* a felhasználó dönt */ }

    /**
     * A nyelvet a felület felépítése ELŐTT kell beállítani, különben az app egy
     * pillanatra a régi nyelven villanna fel.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingMealId = intent.mealIdExtra()

        askForNotificationPermissionInContext()

        setContent {
            MealPilotTheme {
                AppRoot(
                    container = (application as MealPilotApp).container,
                    openMealId = pendingMealId,
                    onMealOpened = { pendingMealId = null },
                )
            }
        }
    }

    /**
     * Az értesítési engedély bekérése — AKKOR, amikor van értelme.
     *
     * Eddig ez a legelső indításkor, az adatfelvétel előtt futott le: a felhasználó
     * még azt sem tudta, mit csinál az app, és már egy rendszerpárbeszéd állt előtte.
     * Aki ilyenkor reflexből elutasítja, az Android 13-tól a MÁSODIK elutasítás után
     * végleg kizárja a kérdést — a `launch` onnantól némán, párbeszéd nélkül tér
     * vissza. Az emlékeztetők így az app egyik fő funkciójából csendben halott
     * funkcióvá váltak, és az appon belül semmi nem mondta meg, miért.
     *
     * Most az adatfelvétel UTÁN kérdezünk, és csak akkor, ha az emlékeztetők
     * egyáltalán be vannak kapcsolva. A blokkolt állapotot a Beállítások képernyő
     * külön kiírja, és onnan a rendszerbeállítás is elérhető.
     */
    private fun askForNotificationPermissionInContext() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        lifecycleScope.launch {
            val settings = (application as MealPilotApp).container.settings.currentSettings()
            if (!settings.onboardingDone) return@launch
            if (!settings.remindersEnabled && !settings.dailySummaryEnabled) return@launch
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingMealId = intent.mealIdExtra()
    }

    private fun Intent.mealIdExtra(): Long? =
        getLongExtra(EXTRA_OPEN_MEAL_ID, -1L).takeIf { it > 0 }

    companion object {
        const val EXTRA_OPEN_MEAL_ID = "open_meal_id"
    }
}
