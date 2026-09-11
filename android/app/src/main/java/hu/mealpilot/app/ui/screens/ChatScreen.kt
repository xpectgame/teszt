package hu.mealpilot.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.mealpilot.app.AppContainer
import hu.mealpilot.app.data.local.ChatMessageEntity
import hu.mealpilot.app.notify.ReminderRefreshWorker
import hu.mealpilot.app.work.GenerationCoordinator
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.ai.AiChatAction
import hu.mealpilot.core.ai.ChatActionType
import hu.mealpilot.core.ai.ChatTurn
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.ai.PlanParser
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
            _busy.value = ChatBusy.Thinking
            container.chatRepository.send(container.mealAi(), text)
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
        val budget = EnergyCalculator.budget(profile)

        return when (action.actionType) {
            ChatActionType.NONE -> "Nincs teendő."

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
                    onProgress = onProgress,
                ).getOrThrow()
                ReminderRefreshWorker.refreshNow(container.appContext)
                "Kész: ${outcome.daysSaved} nap."
            }

            ChatActionType.REGENERATE_DAYS -> {
                val plan = container.planRepository.activePlan()
                    ?: return "Nincs aktív terv, amit át lehetne írni."
                val indexes = action.dayIndexes.filter { it in 0 until plan.dayCount }.distinct()
                if (indexes.isEmpty()) return "Nem találtam, melyik napról van szó."
                var done = 0
                indexes.forEach { index ->
                    container.planRepository.refineDay(
                        ai = container.mealAi(),
                        profile = profile,
                        budget = budget,
                        planId = plan.id,
                        dayIndex = index,
                        instruction = action.instruction,
                    ).onSuccess { done++ }
                }
                ReminderRefreshWorker.refreshNow(container.appContext)
                "$done nap átírva."
            }

            ChatActionType.SET_MEAL_TIMES -> {
                val parsed = action.mealTimes.mapNotNull { entry ->
                    val slot = MealSlot.fromRawOrNull(entry.slot) ?: return@mapNotNull null
                    val time = runCatching { LocalTime.parse(entry.time.trim()) }.getOrNull()
                        ?: return@mapNotNull null
                    slot to time
                }
                if (parsed.isEmpty()) return "Nem értettem, melyik étkezést mikorra tegyem."

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
                val what = parsed.joinToString(", ") { (slot, time) -> "${slot.hu} $time" }
                if (changed > 0) "Átállítva: $what. $changed étkezés időpontja és emlékeztetője frissült."
                else "Átállítva: $what. A következő tervnél már ez lesz az alapértelmezés."
            }

            ChatActionType.SWAP_DAYS -> {
                val plan = container.planRepository.activePlan()
                    ?: return "Nincs aktív terv, amiben cserélni lehetne."
                val indexes = action.dayIndexes.distinct().filter { it in 0 until plan.dayCount }
                if (indexes.size != 2) return "Két napot kell megadni a cseréhez."
                val swapped = container.planRepository.swapDays(plan.id, indexes[0], indexes[1])
                ReminderRefreshWorker.refreshNow(container.appContext)
                if (swapped) "A két nap felcserélve." else "Ezeken a napokon nincs mit cserélni."
            }

            ChatActionType.ADD_RESTRICTIONS -> {
                val added = action.restrictions.mapNotNull(DietRestriction::byName).toSet()
                if (added.isEmpty()) return "Nem ismertem fel a kizárást."
                container.settings.saveProfile(
                    profile.copy(restrictions = profile.restrictions + added)
                )
                "Hozzáadva: ${added.joinToString { it.hu }}. A következő tervnél már érvényes."
            }

            ChatActionType.SET_PREFERENCES -> {
                container.settings.saveProfile(profile.copy(preferences = action.preferences.trim()))
                "A preferenciáid frissültek."
            }

            ChatActionType.ADJUST_RATE -> {
                val rate = action.rateKgPerWeek.coerceIn(0.1, 1.0)
                container.settings.saveProfile(profile.copy(targetRateKgPerWeek = rate))
                val updated = EnergyCalculator.budget(profile.copy(targetRateKgPerWeek = rate))
                "Új ütem: ${"%.2f".format(rate)} kg/hét, napi ${updated.target.kcal} kcal."
            }

            ChatActionType.LOG_WEIGHT -> {
                val kg = action.weightKg
                if (kg !in 35.0..300.0) return "Ez a súly nem tűnik valósnak."
                container.trackingRepository.logWeight(LocalDate.now(), kg, null)
                container.settings.updateWeight(kg, null)
                "Rögzítve: ${"%.1f".format(kg)} kg."
            }
        }
    }
}

private val STARTERS = listOf(
    "Írd át az egész hetet olcsóbbra",
    "Holnap nem érek rá főzni",
    "Mennyi fehérje kell nekem?",
    "Túl gyorsan fogyok",
)

@OptIn(ExperimentalLayoutApi::class)
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
                            "Beszéljük meg",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Kérdezz bármit az étrendedről, a céljaidról vagy a haladásodról. " +
                                "Ha változtatni szeretnél, elég elmondani — megkérdezem, mielőtt bármit átírok.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            STARTERS.forEach { starter ->
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
                                "Nyugodtan zárd be az appot — a háttérben tovább dolgozom.",
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
                    placeholder = { Text("Írj egy üzenetet…") },
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
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Küldés")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageEntity,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fromUser = message.role == ChatTurn.Role.USER.name

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (fromUser) 18.dp else 4.dp,
                        bottomEnd = if (fromUser) 4.dp else 18.dp,
                    )
                )
                .background(
                    if (fromUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (fromUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            )
        }

        AnimatedVisibility(
            visible = message.pendingAction,
            enter = fadeIn() + slideInVertically { it / 2 },
        ) {
            Column(Modifier.padding(top = 8.dp)) {
                Text(
                    message.actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConfirm, enabled = enabled) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Csináld")
                    }
                    TextButton(onClick = onDismiss, enabled = enabled) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Mégse")
                    }
                }
            }
        }
    }
}
