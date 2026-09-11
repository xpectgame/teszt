package hu.mealpilot.app.data.repo

import hu.mealpilot.app.data.local.ChatDao
import hu.mealpilot.app.data.local.ChatMessageEntity
import hu.mealpilot.app.data.local.LogStatus
import hu.mealpilot.app.data.prefs.SettingsRepository
import hu.mealpilot.core.ai.AiChatResponse
import hu.mealpilot.core.ai.ChatContext
import hu.mealpilot.core.ai.ChatTurn
import hu.mealpilot.core.ai.MealAi
import hu.mealpilot.core.ai.PlanParser
import hu.mealpilot.core.energy.EnergyCalculator
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.Nutrients
import hu.mealpilot.core.model.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A beszélgetés tárolása és a modell kontextusának összeállítása.
 *
 * A beszélgetőtárs csak akkor tud érdemben segíteni, ha tudja, hol tart a felhasználó:
 * mi a napi cél, mi van a mai tervben, mennyit evett eddig, hogy halad a súlya. Ezt itt
 * gyűjtjük össze, tömör szöveggé, hogy ne kelljen minden körben nyers adatot küldeni.
 */
class ChatRepository(
    private val chatDao: ChatDao,
    private val planRepository: PlanRepository,
    private val tracking: TrackingRepository,
    private val settings: SettingsRepository,
) {

    fun observeMessages(): Flow<List<ChatMessageEntity>> = chatDao.observeAll()

    suspend fun clear() = chatDao.clear()

    suspend fun dismissAction(messageId: Long) = chatDao.clearPending(messageId)

    /**
     * Elküld egy üzenetet, és elmenti a választ. A felismert műveletet NEM hajtja végre:
     * azt a felület kérdezi meg a felhasználótól, hogy egy félreértett mondat ne írjon át
     * csendben egy egész hónapot.
     */
    suspend fun send(ai: MealAi, text: String): Result<AiChatResponse> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Üres üzenet."))

        // A korábbi ajánlatok elévülnek, amint új kérés jön.
        chatDao.clearAllPending()
        chatDao.insert(
            ChatMessageEntity(
                role = ChatTurn.Role.USER.name,
                body = trimmed,
                sentAtMillis = System.currentTimeMillis(),
            )
        )

        val history = chatDao.all().dropLast(1).map { entity ->
            ChatTurn(
                role = runCatching { ChatTurn.Role.valueOf(entity.role) }.getOrDefault(ChatTurn.Role.USER),
                text = entity.body,
            )
        }

        val response = ai.chat(buildContext(), history, trimmed)
        val value = response.getOrElse { error ->
            chatDao.insert(
                ChatMessageEntity(
                    role = ChatTurn.Role.ASSISTANT.name,
                    body = error.message ?: "Nem sikerült válaszolni.",
                    sentAtMillis = System.currentTimeMillis(),
                )
            )
            return Result.failure(error)
        }

        val action = value.action
        val hasAction = action.actionType != hu.mealpilot.core.ai.ChatActionType.NONE
        chatDao.insert(
            ChatMessageEntity(
                role = ChatTurn.Role.ASSISTANT.name,
                body = value.reply.ifBlank { "Rendben." },
                sentAtMillis = System.currentTimeMillis(),
                actionLabel = if (hasAction) action.confirmLabel.ifBlank { "Végrehajtás" } else "",
                actionJson = if (hasAction) PlanParser.json.encodeToString(action) else "",
                pendingAction = hasAction,
            )
        )
        return Result.success(value)
    }

    suspend fun buildContext(): ChatContext {
        val profile = settings.currentProfile()
        val budget = EnergyCalculator.budget(profile)
        val today = LocalDate.now()
        val plan = planRepository.activePlan()

        val profileSummary = buildString {
            append(if (profile.sex == Sex.MALE) "Férfi" else "Nő")
            append(", ${profile.ageYears} év, ${profile.heightCm.roundToInt()} cm, ")
            append("${"%.1f".format(profile.weightKg)} kg")
            profile.targetWeightKg?.let { append(", célsúly ${"%.1f".format(it)} kg") }
            append(". Étrendi stílus: ${profile.dietStyle.hu}.")
            if (profile.preferences.isNotBlank()) {
                append(" Állandó preferenciák: ${profile.preferences.trim()}")
            }
        }

        val targetSummary = "Napi cél: ${budget.target.kcal} kcal, ${budget.target.proteinG} g fehérje, " +
            "${budget.target.carbsG} g szénhidrát, ${budget.target.fatG} g zsír. " +
            "Alapanyagcsere ${budget.bmr} kcal, napi felhasználás ${budget.tdee} kcal, " +
            "deficit ${budget.appliedDeficit} kcal/nap (${"%.2f".format(budget.expectedRateKgPerWeek)} kg/hét)."

        val planSummary = if (plan == null) {
            "Nincs aktív terv."
        } else {
            val start = LocalDate.ofEpochDay(plan.startEpochDay)
            val dayIndex = (today.toEpochDay() - plan.startEpochDay).toInt()
            buildString {
                append("\"${plan.title}\", ${plan.dayCount} napos, kezdete $start. ")
                if (dayIndex in 0 until plan.dayCount) {
                    append("A mai nap a terv ${dayIndex + 1}. napja, tehát day_index = $dayIndex. ")
                    append("A holnapi day_index = ${dayIndex + 1}.")
                } else {
                    append("A mai nap kívül esik a terven.")
                }
            }
        }

        val todayLogs = tracking.allMealLogs().filter { it.epochDay == today.toEpochDay() }
        val eaten = todayLogs.filter { it.status == LogStatus.EATEN.name || it.status == LogStatus.EXTRA.name }
        val consumed = Nutrients.sum(eaten.map { it.nutrients.toNutrients() })
        val burned = tracking.allActivityLogs()
            .filter { it.epochDay == today.toEpochDay() }
            .sumOf { it.kcalNet }

        val todaySummary = buildString {
            append("Eddig ${consumed.kcal.roundToInt()} kcal és ${consumed.proteinG.roundToInt()} g fehérje ")
            append("${eaten.size} naplózott étkezésből.")
            if (burned > 0) append(" Mozgással ${burned} kcal többlet.")
            if (eaten.isEmpty()) append(" Ma még nem naplózott semmit.")
        }

        val weights = tracking.allWeights().sortedBy { it.epochDay }
        val recentProgress = if (weights.size < 2) {
            ""
        } else {
            val first = weights.first()
            val last = weights.last()
            val change = last.weightKg - first.weightKg
            val days = last.epochDay - first.epochDay
            val direction = if (change < 0) "fogyás" else "hízás"
            "$days nap alatt ${"%.1f".format(abs(change))} kg $direction " +
                "(${"%.1f".format(first.weightKg)} → ${"%.1f".format(last.weightKg)} kg)."
        }

        return ChatContext(
            profileSummary = profileSummary,
            targetSummary = targetSummary,
            planSummary = planSummary,
            todaySummary = todaySummary,
            recentProgress = recentProgress,
            restrictions = DietRestriction.entries.map { it.name },
            availableDayCount = plan?.dayCount ?: 0,
        )
    }
}
