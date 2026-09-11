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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import hu.mealpilot.app.ui.containerFactory
import hu.mealpilot.core.ai.AiChatAction
import hu.mealpilot.core.ai.ChatActionType
import hu.mealpilot.core.ai.ChatTurn
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

/** Mit csinál éppen a beszélgetés. */
sealed interface ChatBusy {
    data object Idle : ChatBusy
    data object Thinking : ChatBusy
    data class Working(val label: String) : ChatBusy
}

class ChatViewModel(private val container: AppContainer) : ViewModel() {

    val messages: StateFlow<List<ChatMessageEntity>> = container.chatRepository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow<ChatBusy>(ChatBusy.Idle)
    val busy: StateFlow<ChatBusy> = _busy.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun consumeToast() { _toast.value = null }

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
        if (_busy.value != ChatBusy.Idle) return
        val action = runCatching {
            PlanParser.json.decodeFromString(AiChatAction.serializer(), message.actionJson)
        }.getOrNull() ?: return

        container.backgroundScope.launch {
            _busy.value = ChatBusy.Working(message.actionLabel.ifBlank { "Dolgozom rajta…" })
            val result = runCatching { execute(action) }
            container.chatRepository.dismissAction(message.id)
            _busy.value = ChatBusy.Idle
            _toast.value = result.getOrNull() ?: result.exceptionOrNull()?.message ?: "Nem sikerült."
        }
    }

    private suspend fun execute(action: AiChatAction): String {
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
    val toast by viewModel.toast.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    Column(Modifier.fillMaxSize()) {
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
                    enabled = busy == ChatBusy.Idle,
                    onConfirm = { viewModel.confirm(message) },
                    onDismiss = { viewModel.dismiss(message) },
                )
            }

            if (busy != ChatBusy.Idle) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            when (val current = busy) {
                                is ChatBusy.Working -> current.label
                                else -> "Gondolkodom…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (busy is ChatBusy.Working) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
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
                    enabled = draft.isNotBlank() && busy == ChatBusy.Idle,
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
