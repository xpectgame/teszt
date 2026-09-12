package hu.mealpilot.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.R
import hu.mealpilot.app.data.local.ChatMessageEntity
import hu.mealpilot.app.notify.ReminderRefreshWorker
import hu.mealpilot.app.work.GenerationCoordinator
import hu.mealpilot.app.data.ai.QuotaExceededException
import hu.mealpilot.app.data.repo.ReportKind
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.ui.components.GroupLabel
import hu.mealpilot.app.ui.components.ReportDialog
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.app.ui.theme.PlateShape
import hu.mealpilot.core.ai.AiChatAction
import hu.mealpilot.core.ai.ChatActionType
import hu.mealpilot.core.ai.ChatTurn
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.i18n.label
import hu.mealpilot.core.ai.PlanParser
import hu.mealpilot.core.billing.PaidFeature
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.model.DietRestriction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** A beszélgetés saját, rövid várakozása. A hosszú műveletek a koordinátoron futnak. */
enum class ChatBusy { Idle, Thinking }

class ChatViewModel(private val container: AppContainer) : ViewModel() {

    val messages: StateFlow<List<ChatMessageEntity>> = container.chatRepository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow<ChatBusy>(ChatBusy.Idle)
    val busy: StateFlow<ChatBusy> = _busy.asStateFlow()

    /** A hosszú műveletek a közös koordinátoron futnak, hogy mindenhol látszódjanak. */
    val generation: StateFlow<GenerationCoordinator.Status> = container.generation.status
    val actionResult: StateFlow<String?> = container.generation.actionResult

    fun consumeActionResult() = container.generation.consumeActionResult()

    fun send(text: String) {
        if (_busy.value != ChatBusy.Idle) return
        viewModelScope.launch {
            val entitlement = container.entitlements.current()
            entitlement.blockReason(PaidFeature.CHAT)?.let { reason ->
                container.generation.requestPaywall(reason)
                return@launch
            }
            _busy.value = ChatBusy.Thinking
            container.telemetry.record(TelemetryEvent.CHAT_MESSAGE)
            val result = container.chatRepository.send(container.mealAi(), text, container.language)
            if (result.isSuccess) container.entitlements.recordChatMessage()
            // A helyi számláló megelőzi ezt, de a végső szó a szerveré: ha ő utasít el
            // kvóta miatt, akkor is az előfizetést ajánljuk fel, ne hibaüzenetet.
            (result.exceptionOrNull() as? QuotaExceededException)
                ?.takeIf { it.upgradeOffered }
                ?.let { container.generation.requestPaywall(it.message ?: "Elfogyott a havi keret.") }
            _busy.value = ChatBusy.Idle
        }
    }

    fun dismiss(message: ChatMessageEntity) = viewModelScope.launch {
        container.chatRepository.dismissAction(message.id)
    }

    /**
     * A felismert műveletet csak megerősítés után hajtjuk végre. Egy félreértett mondat
     * ne írjon át csendben egy egész hónapot.
     */
    fun confirm(message: ChatMessageEntity) {
        if (_busy.value != ChatBusy.Idle || container.generation.isBusy) return
        val action = runCatching {
            PlanParser.json.decodeFromString(AiChatAction.serializer(), message.actionJson)
        }.getOrNull() ?: return

        viewModelScope.launch {
            container.telemetry.record(TelemetryEvent.CHAT_ACTION_CONFIRMED)
            container.entitlements.current().blockReason(PaidFeature.CHAT_ACTIONS)?.let { reason ->
                container.generation.requestPaywall(reason)
                return@launch
            }
            runConfirmed(message, action)
        }
    }

    private fun runConfirmed(message: ChatMessageEntity, action: AiChatAction) {
        container.generation.runAction(message.actionLabel.ifBlank { "Dolgozom rajta" }) { progress ->
            val result = execute(action, progress)
            container.chatRepository.dismissAction(message.id)
            result
        }
    }

    private suspend fun execute(
        action: AiChatAction,
        onProgress: (GenerationProgress) -> Unit = {},
    ): String {
        val profile = container.settings.currentProfile()
        val budget = EnergyCalculator.budget(profile, container.language)
        fun text(resId: Int, vararg args: Any) = container.appContext.getString(resId, *args)

        return when (action.actionType) {
            ChatActionType.NONE -> text(R.string.chat_nothing_to_do)

            ChatActionType.CREATE_PLAN, ChatActionType.REGENERATE_PLAN -> {
                val existing = container.planRepository.activePlan()
                val days = when {
                    action.days > 0 -> action.days.coerceIn(1, 30)
                    existing != null -> existing.dayCount
                    else -> 7
                }
                val start = if (action.actionType == ChatActionType.REGENERATE_PLAN && existing != null) {
                    maxOf(LocalDate.ofEpochDay(existing.startEpochDay), LocalDate.now())
                } else {
                    LocalDate.now()
                }
                val outcome = container.planRepository.generateAndSave(
                    ai = container.mealAi(),
                    profile = profile,
                    budget = budget,
                    startDate = start,
                    days = days,
                    freeText = listOf(profile.preferences, action.instruction)
                        .filter { it.isNotBlank() }
                        .joinToString(". "),
                    language = container.language,
                    onProgress = onProgress,
                ).getOrThrow()
                ReminderRefreshWorker.refreshNow(container.appContext)
                text(R.string.chat_done_days, outcome.daysSaved)
            }

            ChatActionType.REGENERATE_DAYS -> {
                val plan = container.planRepository.activePlan()
                    ?: return text(R.string.chat_no_plan_to_rewrite)
                val indexes = action.dayIndexes.filter { it in 0 until plan.dayCount }.distinct()
                if (indexes.isEmpty()) return text(R.string.chat_which_day)
                var done = 0
                indexes.forEach { index ->
                    container.planRepository.refineDay(
                        ai = container.mealAi(),
                        profile = profile,
                        budget = budget,
                        planId = plan.id,
                        dayIndex = index,
                        instruction = action.instruction,
                        language = container.language,
                    ).onSuccess { done++ }
                }
                ReminderRefreshWorker.refreshNow(container.appContext)
                text(R.string.chat_days_rewritten, done)
            }

            ChatActionType.SET_MEAL_TIMES -> {
                val parsed = action.mealTimes.mapNotNull { entry ->
                    val slot = MealSlot.fromRawOrNull(entry.slot) ?: return@mapNotNull null
                    val time = runCatching { LocalTime.parse(entry.time.trim()) }.getOrNull()
                        ?: return@mapNotNull null
                    slot to time
                }
                if (parsed.isEmpty()) return text(R.string.chat_which_meal_time)

                // A profil alapértelmezése is frissül, hogy a jövőbeli tervek is ezt használják.
                val slots = MealSlot.forMealsPerDay(profile.mealsPerDay)
                val times = MutableList(slots.size) { i ->
                    profile.mealTimes.getOrNull(i) ?: slots[i].defaultTime
                }
                parsed.forEach { (slot, time) ->
                    val index = slots.indexOf(slot)
                    if (index >= 0) times[index] = time.toString()
                }
                container.settings.saveProfile(profile.copy(mealTimes = times))

                val plan = container.planRepository.activePlan()
                val changed = if (plan == null) 0 else container.planRepository.setMealTimes(
                    planId = plan.id,
                    slotTimes = parsed.associate { (slot, time) -> slot.name to time },
                    dayIndexes = action.dayIndexes,
                )
                ReminderRefreshWorker.refreshNow(container.appContext)
                val what = parsed.joinToString(", ") { (slot, time) -> "${slot.label(container.language)} $time" }
                if (changed > 0) text(R.string.chat_times_set, what, changed)
                else text(R.string.chat_times_set_default, what)
            }

            ChatActionType.SWAP_DAYS -> {
                val plan = container.planRepository.activePlan()
                    ?: return text(R.string.chat_no_plan_to_swap)
                val indexes = action.dayIndexes.distinct().filter { it in 0 until plan.dayCount }
                if (indexes.size != 2) return text(R.string.chat_need_two_days)
                val swapped = container.planRepository.swapDays(plan.id, indexes[0], indexes[1])
                ReminderRefreshWorker.refreshNow(container.appContext)
                if (swapped) text(R.string.chat_days_swapped) else text(R.string.chat_nothing_to_swap)
            }

            ChatActionType.ADD_RESTRICTIONS -> {
                val added = action.restrictions.mapNotNull(DietRestriction::byName).toSet()
                if (added.isEmpty()) return text(R.string.chat_unknown_restriction)
                container.settings.saveProfile(
                    profile.copy(restrictions = profile.restrictions + added)
                )
                text(
                    R.string.chat_restrictions_added,
                    added.joinToString { it.label(container.language) },
                )
            }

            ChatActionType.SET_PREFERENCES -> {
                container.settings.saveProfile(profile.copy(preferences = action.preferences.trim()))
                text(R.string.chat_preferences_updated)
            }

            ChatActionType.ADJUST_RATE -> {
                val rate = action.rateKgPerWeek.coerceIn(0.1, 1.0)
                container.settings.saveProfile(profile.copy(targetRateKgPerWeek = rate))
                val updated = EnergyCalculator.budget(profile.copy(targetRateKgPerWeek = rate), container.language)
                text(R.string.chat_rate_set, "%.2f".format(rate), updated.target.kcal)
            }

            ChatActionType.LOG_WEIGHT -> {
                val kg = action.weightKg
                if (kg !in 35.0..300.0) return text(R.string.chat_weight_unrealistic)
                container.trackingRepository.logWeight(LocalDate.now(), kg, null)
                container.settings.updateWeight(kg, null)
                text(R.string.weight_logged, "%.1f".format(kg))
            }
        }
    }
}

private val STARTERS = listOf(
    R.string.chat_starter_cheaper,
    R.string.chat_starter_no_time,
    R.string.chat_starter_protein,
    R.string.chat_starter_too_fast,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
) {
    val viewModel: ChatViewModel = viewModel(factory = containerFactory(container) { ChatViewModel(it) })
    val messages by viewModel.messages.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val generation by viewModel.generation.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()
    val locked = busy != ChatBusy.Idle || generation.running
    var draft by remember { mutableStateOf("") }
    var reportedMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(messages.size, busy, imeVisible, generation.running) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(actionResult) {
        actionResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionResult()
        }
    }

    // enableEdgeToEdge mellett az ablak nem méreteződik át magától a billentyűzethez,
    // ezért az IME magasságát kézzel kell a tartalom alá tenni.
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Column {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.chat_header),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.chat_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.chat_disclaimer),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            STARTERS.forEach { starterRes ->
                                val starter = stringResource(starterRes)
                                SuggestionChip(
                                    onClick = { viewModel.send(starter) },
                                    label = { Text(starter) },
                                )
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    enabled = !locked,
                    onConfirm = { viewModel.confirm(message) },
                    onDismiss = { viewModel.dismiss(message) },
                    onReport = { reportedMessage = message.body },
                )
            }

            if (locked) {
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(10.dp))
                            Text(
                                if (generation.running) generation.headline else "Gondolkodom…",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (generation.running && generation.detail.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                generation.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 26.dp),
                            )
                            Text(
                                stringResource(R.string.chat_background_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 26.dp, top = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        if (generation.running) {
            val fraction = generation.fraction
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                )
                FilledIconButton(
                    onClick = {
                        viewModel.send(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank() && !locked,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                }
            }
        }
    }

    reportedMessage?.let { body ->
        ReportDialog(
            container = container,
            kind = ReportKind.CHAT,
            payload = body,
            onDismiss = { reportedMessage = null },
            onResult = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessageEntity,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onReport: () -> Unit,
) {
    val fromUser = message.role == ChatTurn.Role.USER.name

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                // A gépi válaszra hosszan nyomva lehet jelenteni. Nem teszünk minden
                // buborék alá gombot: az ritkán kell, és minden beszélgetést elcsúfítana.
                .then(
                    if (fromUser) Modifier
                    else Modifier.combinedClickable(onClick = {}, onLongClick = onReport)
                )
                .clip(
                    RoundedCornerShape(
                        topStart = 22.dp,
                        topEnd = 22.dp,
                        bottomStart = if (fromUser) 22.dp else 7.dp,
                        bottomEnd = if (fromUser) 7.dp else 22.dp,
                    )
                )
                // A gépi válasz fehér lapon ül, a sajátunk zöldön. A korábbi krém
                // háttér a krém alapon szinte nem is látszott: a válasz elolvadt
                // a képernyőben.
                .background(
                    if (fromUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Text(
                message.body,
                style = MaterialTheme.typography.bodyLarge,
                color = if (fromUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            )
        }

        AnimatedVisibility(
            visible = message.pendingAction,
            enter = fadeIn() + slideInVertically { it / 2 },
        ) {
            // A jóváhagyáskérés nem egy buborék alá írt mondat: saját agyagszínű
            // lapot kap, mert ez az egyetlen pont, ahol a beszélgetés VÁR valamire.
            Column(
                Modifier
                    .padding(top = 10.dp)
                    .widthIn(max = 300.dp)
                    .clip(PlateShape.hero)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(17.dp),
            ) {
                GroupLabel(
                    stringResource(R.string.chat_needs_approval),
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    message.actionLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = onConfirm,
                        enabled = enabled,
                        shape = PlateShape.innerButton,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.chat_do_it))
                    }
                    TextButton(onClick = onDismiss, enabled = enabled) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}
