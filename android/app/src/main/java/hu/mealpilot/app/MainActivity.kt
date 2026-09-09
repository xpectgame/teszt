package hu.mealpilot.app

import android.Manifest
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
import hu.mealpilot.app.ui.AppRoot
import hu.mealpilot.app.ui.theme.MealPilotTheme

class MainActivity : ComponentActivity() {

    private var pendingMealId by mutableStateOf<Long?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* a felhasználó dönt */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingMealId = intent.mealIdExtra()

        // Csak akkor kérdezünk, ha még nincs meg — így nem villan fel minden indításkor.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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
