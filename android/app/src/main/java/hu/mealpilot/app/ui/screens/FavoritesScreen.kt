package hu.mealpilot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.R
import hu.mealpilot.app.data.local.FavoriteWithIngredients
import hu.mealpilot.app.data.repo.PlanRepository
import hu.mealpilot.app.i18n.LocalAppLanguage
import hu.mealpilot.app.ui.components.BackButton
import hu.mealpilot.app.ui.components.EmptyState
import hu.mealpilot.app.ui.components.PlatePill
import hu.mealpilot.app.ui.components.SectionCard
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.app.ui.icon
import hu.mealpilot.app.ui.theme.LocalDarkTheme
import hu.mealpilot.app.ui.theme.MealColors
import hu.mealpilot.app.ui.theme.MealLabelStyle
import hu.mealpilot.app.ui.components.MealStamp
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.i18n.label
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class FavoritesViewModel(private val container: AppContainer) : ViewModel() {

    val favorites: StateFlow<List<FavoriteWithIngredients>> =
        container.favoriteRepository.observeFavorites()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(id: Long) = viewModelScope.launch { container.favoriteRepository.remove(id) }
}

/**
 * A megjelölt fogások listája.
 *
 * A recept IDE is átkerül, nem csak a név: a kedvenc akkor ér valamit, ha meg is lehet
 * főzni belőle az ételt — akkor is, ha a terv, amiben szerepelt, már nincs meg.
 */
@Composable
fun FavoritesScreen(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val viewModel: FavoritesViewModel = viewModel(
        factory = containerFactory(container) { FavoritesViewModel(it) },
    )
    val favorites by viewModel.favorites.collectAsState()
    val language = LocalAppLanguage.current
    val dark = LocalDarkTheme.current
    val scope = rememberCoroutineScope()
    // A @Composable hívás a lambdában fordítási hiba lenne; itt olvassuk ki.
    val removedText = stringResource(R.string.favorites_removed)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackButton(stringResource(R.string.action_back), onBack)
            Text(
                stringResource(R.string.favorites_title),
                style = MaterialTheme.typography.headlineLarge,
            )
        }

        if (favorites.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.favorites_empty_title),
                    message = stringResource(R.string.favorites_empty_message),
                )
            }
            return@LazyColumn
        }

        item {
            Text(
                stringResource(R.string.favorites_planner_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(favorites, key = { it.favorite.id }) { entry ->
            val favorite = entry.favorite
            val slot = MealSlot.fromRaw(favorite.slot)
            val accent = MealColors.of(slot.ordinal, dark)

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MealStamp(icon = slot.icon, color = accent, done = false, size = 52.dp)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            slot.label(language).uppercase(),
                            style = MealLabelStyle,
                            color = accent,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(favorite.name, style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(
                        onClick = {
                            viewModel.remove(favorite.id)
                            scope.launch { snackbarHostState.showSnackbar(removedText) }
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.favorites_delete),
                        )
                    }
                }

                if (favorite.description.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        favorite.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlatePill(
                        stringResource(
                            R.string.meal_kcal_value,
                            favorite.nutrients.kcal.roundToInt(),
                        )
                    )
                    PlatePill(
                        stringResource(R.string.meal_minutes, favorite.prepMinutes),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    PlatePill(
                        pluralStringResource(
                            R.plurals.favorites_ingredients_count,
                            entry.ingredients.size,
                            entry.ingredients.size,
                        ),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                val steps = PlanRepository.decodeStrings(favorite.recipeStepsJson)
                if (steps.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    steps.forEachIndexed { index, step ->
                        Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.favorites_added_on, favorite.addedAtMillis.asDayText()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A megjelölés napja a felület nyelvén.
 *
 * A mintát erőforrásból vesszük, ahogy a napi fejléc is: kézzel írt hónapnevekkel
 * minden új nyelvnél újra kellene, és a ragozás is elromlana.
 */
@Composable
private fun Long.asDayText(): String {
    val pattern = stringResource(R.string.date_pattern)
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
