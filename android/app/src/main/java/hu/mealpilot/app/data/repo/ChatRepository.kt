package hu.mealpilot.app.data.repo

import java.util.Locale
import hu.mealpilot.core.i18n.label
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.app.R
import android.content.res.Configuration
import android.content.Context
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
    private val context: Context,
    private val chatDao: ChatDao,
    private val planRepository: PlanRepository,
    private val tracking: TrackingRepository,
    private val settings: SettingsRepository,
) {

    /**
     * A felhasználónak szóló szövegek a felület nyelvén.
     *
     * Az alkalmazáskontextus a rendszer nyelvét hordozza, nem a felhasználó választását,
     * ezért a nyelvet külön ráhúzzuk — enélkül egy magyar rendszernyelvű telefonon az
     * angolra kapcsolt app magyarul mentené a hibaüzeneteket az előzménybe.
     */
    private fun string(resId: Int, language: AppLanguage): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.tag))
        return context.createConfigurationContext(config).getString(resId)
    }

    fun observeMessages(): Flow<List<ChatMessageEntity>> = chatDao.observeAll()

    suspend fun clear() = chatDao.clear()

    suspend fun dismissAction(messageId: Long) = chatDao.clearPending(messageId)

    /**
     * Elküld egy üzenetet, és elmenti a választ. A felismert műveletet NEM hajtja végre:
     * azt a felület kérdezi meg a felhasználótól, hogy egy félreértett mondat ne írjon át
     * csendben egy egész hónapot.
     */
    suspend fun send(
        ai: MealAi,
        text: String,
        language: AppLanguage = AppLanguage.DEFAULT,
    ): Result<AiChatResponse> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException(string(R.string.chat_empty_message, language)))

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

        val response = ai.chat(buildContext(language), history, trimmed)
        val value = response.getOrElse { error ->
            chatDao.insert(
                ChatMessageEntity(
                    role = ChatTurn.Role.ASSISTANT.name,
                    body = error.message ?: string(R.string.chat_no_answer, language),
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
                body = value.reply.ifBlank { string(R.string.chat_ok, language) },
                sentAtMillis = System.currentTimeMillis(),
                actionLabel = if (hasAction) action.confirmLabel.ifBlank { string(R.string.chat_do_it, language) } else "",
                actionJson = if (hasAction) PlanParser.json.encodeToString(action) else "",
                pendingAction = hasAction,
            )
        )
        return Result.success(value)
    }

    /**
     * A beszélgetés kontextusa.
     *
     * Ez a szöveg a promptba megy, nem a felületre — ezért NEM erőforrásból jön, hanem
     * a terv nyelvén épül fel. Egy magyar kontextus angol beszélgetésben nem hiba
     * lenne, hanem félrevezetés: a modell abból a nyelvből következtet arra, milyen
     * nyelven válaszoljon és milyen fogásokat ajánljon.
     */
    suspend fun buildContext(language: AppLanguage = AppLanguage.DEFAULT): ChatContext {
        val profile = settings.currentProfile()
        val budget = EnergyCalculator.budget(profile, language)
        val today = LocalDate.now()
        val plan = planRepository.activePlan()
        val english = language == AppLanguage.EN
        fun s(hungarian: String, englishText: String) = if (english) englishText else hungarian

        val profileSummary = buildString {
            append(
                if (profile.sex == Sex.MALE) s("Férfi", "Male") else s("Nő", "Female")
            )
            append(s(", ${profile.ageYears} év, ", ", ${profile.ageYears} years old, "))
            append("${profile.heightCm.roundToInt()} cm, ")
            append("${"%.1f".format(profile.weightKg)} kg")
            profile.targetWeightKg?.let {
                append(s(", célsúly ${"%.1f".format(it)} kg", ", target weight ${"%.1f".format(it)} kg"))
            }
            append(s(". Étrendi stílus: ", ". Diet style: ") + "${profile.dietStyle.label(language)}.")
            if (profile.preferences.isNotBlank()) {
                append(s(" Állandó preferenciák: ", " Standing preferences: ") + profile.preferences.trim())
            }
        }

        val targetSummary = if (english) {
            "Daily target: ${budget.target.kcal} kcal, ${budget.target.proteinG} g protein, " +
                "${budget.target.carbsG} g carbs, ${budget.target.fatG} g fat. " +
                "BMR ${budget.bmr} kcal, daily burn ${budget.tdee} kcal, " +
                "deficit ${budget.appliedDeficit} kcal/day (${"%.2f".format(budget.expectedRateKgPerWeek)} kg/week)."
        } else {
            "Napi cél: ${budget.target.kcal} kcal, ${budget.target.proteinG} g fehérje, " +
                "${budget.target.carbsG} g szénhidrát, ${budget.target.fatG} g zsír. " +
                "Alapanyagcsere ${budget.bmr} kcal, napi felhasználás ${budget.tdee} kcal, " +
                "deficit ${budget.appliedDeficit} kcal/nap (${"%.2f".format(budget.expectedRateKgPerWeek)} kg/hét)."
        }

        val planSummary = if (plan == null) {
            s("Nincs aktív terv.", "No active plan.")
        } else {
            val start = LocalDate.ofEpochDay(plan.startEpochDay)
            val dayIndex = (today.toEpochDay() - plan.startEpochDay).toInt()
            buildString {
                append("\"${plan.title}\", ")
                append(
                    s(
                        "${plan.dayCount} napos, kezdete $start. ",
                        "${plan.dayCount} days, starting $start. ",
                    )
                )
                if (dayIndex in 0 until plan.dayCount) {
                    append(
                        s(
                            "A mai nap a terv ${dayIndex + 1}. napja, tehát day_index = $dayIndex. " +
                                "A holnapi day_index = ${dayIndex + 1}.",
                            "Today is day ${dayIndex + 1} of the plan, so day_index = $dayIndex. " +
                                "Tomorrow's day_index = ${dayIndex + 1}.",
                        )
                    )
                } else {
                    append(s("A mai nap kívül esik a terven.", "Today falls outside the plan."))
                }
            }
        }

        val todayLogs = tracking.allMealLogs().filter { it.epochDay == today.toEpochDay() }
        val eaten = todayLogs.filter {
            it.status == LogStatus.EATEN.name ||
                it.status == LogStatus.REPLACED.name ||
                it.status == LogStatus.EXTRA.name
        }
        val consumed = Nutrients.sum(eaten.map { it.nutrients.toNutrients() })

        val todaySummary = buildString {
            if (english) {
                append("${consumed.kcal.roundToInt()} kcal and ${consumed.proteinG.roundToInt()} g protein ")
                append("so far, from ${eaten.size} logged meals.")
                if (eaten.isEmpty()) append(" Nothing logged today yet.")
            } else {
                append("Eddig ${consumed.kcal.roundToInt()} kcal és ${consumed.proteinG.roundToInt()} g fehérje ")
                append("${eaten.size} naplózott étkezésből.")
                if (eaten.isEmpty()) append(" Ma még nem naplózott semmit.")
            }
        }

        val weights = tracking.allWeights().sortedBy { it.epochDay }
        val recentProgress = if (weights.size < 2) {
            ""
        } else {
            val first = weights.first()
            val last = weights.last()
            val change = last.weightKg - first.weightKg
            val days = last.epochDay - first.epochDay
            if (english) {
                val direction = if (change < 0) "lost" else "gained"
                "${"%.1f".format(abs(change))} kg $direction over $days days " +
                    "(${"%.1f".format(first.weightKg)} → ${"%.1f".format(last.weightKg)} kg)."
            } else {
                val direction = if (change < 0) "fogyás" else "hízás"
                "$days nap alatt ${"%.1f".format(abs(change))} kg $direction " +
                    "(${"%.1f".format(first.weightKg)} → ${"%.1f".format(last.weightKg)} kg)."
            }
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
