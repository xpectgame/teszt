package hu.mealpilot.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.ui.screens.ActivityScreen
import hu.mealpilot.app.ui.screens.MealDetailScreen
import hu.mealpilot.app.ui.screens.OnboardingScreen
import hu.mealpilot.app.ui.screens.PlanScreen
import hu.mealpilot.app.ui.screens.ProfileScreen
import hu.mealpilot.app.ui.screens.SettingsScreen
import hu.mealpilot.app.ui.screens.ShoppingScreen
import hu.mealpilot.app.ui.screens.TodayScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val TODAY = "today"
    const val PLAN = "plan"
    const val SHOPPING = "shopping"
    const val ACTIVITY = "activity"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val MEAL = "meal/{mealId}"

    fun meal(id: Long) = "meal/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Ma", Icons.Filled.RestaurantMenu),
    Tab(Routes.PLAN, "Terv", Icons.Filled.CalendarMonth),
    Tab(Routes.SHOPPING, "Bevásárlás", Icons.Filled.ShoppingCart),
    Tab(Routes.ACTIVITY, "Mozgás", Icons.Filled.DirectionsRun),
    Tab(Routes.PROFILE, "Én", Icons.Filled.Person),
)

@Composable
fun AppRoot(
    container: AppContainer,
    openMealId: Long?,
    onMealOpened: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by container.settings.settings.collectAsState(initial = null)

    // Az értesítésből érkező étkezés megnyitása.
    LaunchedEffect(openMealId) {
        if (openMealId != null) {
            navController.navigate(Routes.meal(openMealId))
            onMealOpened()
        }
    }

    val onboardingDone = settings?.onboardingDone

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (onboardingDone == true) {
                BottomBar(navController)
            }
        },
    ) { padding ->
        when (onboardingDone) {
            null -> Unit // a beállítások betöltéséig nem villantunk fel képernyőt
            false -> OnboardingScreen(
                container = container,
                modifier = Modifier.padding(padding),
            )
            true -> NavHost(
                navController = navController,
                startDestination = Routes.TODAY,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.TODAY) {
                    TodayScreen(
                        container = container,
                        snackbarHostState = snackbarHostState,
                        onOpenMeal = { navController.navigate(Routes.meal(it)) },
                        onCreatePlan = { navController.navigate(Routes.PLAN) },
                    )
                }
                composable(Routes.PLAN) {
                    PlanScreen(
                        container = container,
                        snackbarHostState = snackbarHostState,
                        onOpenMeal = { navController.navigate(Routes.meal(it)) },
                    )
                }
                composable(Routes.SHOPPING) {
                    ShoppingScreen(container = container, snackbarHostState = snackbarHostState)
                }
                composable(Routes.ACTIVITY) {
                    ActivityScreen(container = container, snackbarHostState = snackbarHostState)
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        container = container,
                        snackbarHostState = snackbarHostState,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        container = container,
                        snackbarHostState = snackbarHostState,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.MEAL) { entry ->
                    val mealId = entry.arguments?.getString("mealId")?.toLongOrNull()
                    MealDetailScreen(
                        container = container,
                        mealId = mealId ?: -1L,
                        snackbarHostState = snackbarHostState,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        tabs.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
