package hu.mealpilot.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import hu.mealpilot.app.ui.screens.ChatScreen
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
    const val CHAT = "chat"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val MEAL = "meal/{mealId}"

    fun meal(id: Long) = "meal/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

// A mozgásnaplózás kikerült az alsó sávból: a napi kalóriakeret mellett a helye, a Ma
// képernyőről nyílik. A felszabadult hely a beszélgetésé, ami sokkal gyakrabban kell.
private val tabs = listOf(
    Tab(Routes.TODAY, "Ma", Icons.Filled.RestaurantMenu),
    Tab(Routes.PLAN, "Terv", Icons.Filled.CalendarMonth),
    Tab(Routes.SHOPPING, "Bevásárlás", Icons.Filled.ShoppingCart),
    Tab(Routes.CHAT, "Beszéljünk", Icons.AutoMirrored.Filled.Chat),
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
    val generationStatus by container.generation.status.collectAsState()

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
                Column {
                    GenerationBanner(
                        status = generationStatus,
                        onClick = {
                            navController.navigate(Routes.PLAN) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                    BottomBar(navController)
                }
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
                        onOpenActivity = { navController.navigate(Routes.ACTIVITY) },
                    )
                }
                composable(Routes.CHAT) {
                    ChatScreen(container = container, snackbarHostState = snackbarHostState)
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
                    ActivityScreen(
                        container = container,
                        snackbarHostState = snackbarHostState,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        container = container,
                        snackbarHostState = snackbarHostState,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenActivity = { navController.navigate(Routes.ACTIVITY) },
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

/**
 * Vékony sáv az alsó navigáció fölött, amíg a tervezés fut.
 *
 * A munka nem a Terv képernyőhöz tartozik, hanem az apphoz — így bármelyik fülön látszik,
 * hogy halad, és egy koppintással oda lehet ugrani, ahol a napok megjelennek.
 */
@Composable
private fun GenerationBanner(
    status: hu.mealpilot.app.work.GenerationCoordinator.Status,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = status.running,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Column {
                val fraction = status.fraction
                if (fraction != null) {
                    val animated by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(durationMillis = 500),
                        label = "generation-progress",
                    )
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            status.headline.ifBlank { "Dolgozom rajta" },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            status.detail,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (status.hasUsableDays) {
                        Text("Megnézem", style = MaterialTheme.typography.labelLarge)
                    }
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
