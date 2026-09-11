package hu.mealpilot.app.data.repo

import hu.mealpilot.app.data.ai.MealAiException
import hu.mealpilot.app.data.local.IngredientEntity
import hu.mealpilot.app.data.local.MealDao
import hu.mealpilot.app.data.local.MealEntity
import hu.mealpilot.app.data.local.MealWithIngredients
import hu.mealpilot.app.data.local.NutrientsColumns
import hu.mealpilot.app.data.local.PlanDao
import hu.mealpilot.app.data.local.PlanEntity
import hu.mealpilot.app.data.local.ShoppingDao
import hu.mealpilot.app.data.local.ShoppingItemEntity
import hu.mealpilot.app.data.local.endEpochDay
import hu.mealpilot.core.ai.AiDay
import hu.mealpilot.core.ai.AiIngredient
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.ai.MealAi
import hu.mealpilot.core.ai.PlanParser
import hu.mealpilot.core.ai.PlanRequest
import hu.mealpilot.core.energy.EnergyBudget
import hu.mealpilot.core.model.UserProfile
import hu.mealpilot.core.shopping.ShoppingListBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A tervek életciklusa: generálás → mentés → bevásárlólista → napi lekérdezések.
 */
/**
 * Egy tervgenerálás eredménye. A generálás félúton is megszakadhat — ilyenkor a már
 * elkészült napok megmaradnak, mert egy fél terv sokkal többet ér, mint a semmi.
 */
data class PlanGenerationOutcome(
    val planId: Long,
    val daysSaved: Int,
    val requestedDays: Int,
    val error: Throwable? = null,
    /** Igaz, ha a terv egészét vagy egy részét a beépített tervező rakta ki. */
    val usedFallback: Boolean = false,
) {
    val isComplete: Boolean get() = error == null && daysSaved >= requestedDays
}

class PlanRepository(
    private val planDao: PlanDao,
    private val mealDao: MealDao,
    private val shoppingDao: ShoppingDao,
) {

    fun observeActivePlan(): Flow<PlanEntity?> = planDao.observeActive()
    fun observeAllPlans(): Flow<List<PlanEntity>> = planDao.observeAll()
    fun observeDay(date: LocalDate): Flow<List<MealWithIngredients>> = mealDao.observeDay(date.toEpochDay())
    fun observePlanMeals(planId: Long): Flow<List<MealWithIngredients>> = mealDao.observePlan(planId)
    fun observeMeal(mealId: Long): Flow<MealWithIngredients?> = mealDao.observeMeal(mealId)

    suspend fun activePlan(): PlanEntity? = planDao.activePlan()

    /**
     * Legenerálja és elmenti a tervet, szakaszonként.
     *
     * A terv sora azonnal létrejön, és minden elkészült szakasz napjai rögtön bekerülnek
     * az adatbázisba. A felület a tervet figyeli, ezért az első napok másodpercek alatt
     * megjelennek és használhatók, miközben a többi még töltődik. Korábban az egész
     * hónapot meg kellett várni, mire bármi látszott.
     */
    suspend fun generateAndSave(
        ai: MealAi,
        profile: UserProfile,
        budget: EnergyBudget,
        startDate: LocalDate,
        days: Int,
        freeText: String,
        onProgress: (GenerationProgress) -> Unit = {},
    ): Result<PlanGenerationOutcome> {
        val previousNames = planDao.activePlan()?.let { mealDao.namesInPlan(it.id) } ?: emptyList()

        val request = PlanRequest(
            profile = profile,
            budget = budget,
            days = days,
            startDayIndex = 0,
            totalDays = days,
            freeText = freeText,
            avoidRecipes = previousNames.takeLast(40),
            startWeekday = startDate.hungarianWeekday(),
        )

        val planId = planDao.insert(
            PlanEntity(
                title = "Étrend készül…",
                summary = "",
                startEpochDay = startDate.toEpochDay(),
                dayCount = days,
                createdAtMillis = System.currentTimeMillis(),
                requestText = freeText,
                targetKcal = budget.target.kcal,
                targetProteinG = budget.target.proteinG,
                targetCarbsG = budget.target.carbsG,
                targetFatG = budget.target.fatG,
                targetFiberG = budget.target.fiberG,
                // Az új terv addig nem lép életbe, amíg nincs benne egyetlen nap sem.
                // Így egy megszakadt generálás nem veszi el a működő tervet.
                isActive = false,
            )
        )

        var savedDays = 0
        val coachNotes = mutableListOf<String>()

        val result = ai.generatePlan(
            request = request,
            onProgress = onProgress,
            onChunk = { chunk ->
                val isFirstChunk = savedDays == 0
                chunk.days.forEach { day -> insertDay(planId, startDate, day) }
                savedDays += chunk.days.size
                coachNotes += chunk.coachNotes

                planDao.byId(planId)?.let { current ->
                    planDao.update(
                        current.copy(
                            title = current.title.takeIf { it != "Étrend készül…" }
                                ?: chunk.planTitle.ifBlank { "Étrend – $startDate" },
                            summary = current.summary.ifBlank { chunk.summary },
                            coachNotesJson = encodeStrings(coachNotes.distinct().take(4)),
                            isActive = true,
                        )
                    )
                }

                // Az első kész szakasznál váltunk: innentől ez az aktív terv, a korábbiak
                // pedig törlődnek. A régit nem elég deaktiválni — a naplózás és a napi
                // nézet átfedő dátumoknál egyébként duplán mutatná az étkezéseket.
                if (isFirstChunk) {
                    planDao.deleteOthers(planId)
                }
                rebuildShoppingList(planId, startDate.toEpochDay(), startDate.toEpochDay() + days - 1)
            },
        )

        val error = result.exceptionOrNull()
        if (savedDays == 0) {
            planDao.delete(planId)
            return Result.failure(error ?: MealAiException("A válasz egyetlen napot sem tartalmazott."))
        }

        return Result.success(
            PlanGenerationOutcome(
                planId = planId,
                daysSaved = savedDays,
                requestedDays = days,
                error = error,
            )
        )
    }

    /** Egy nap újratervezése szabad szöveges kérés alapján. */
    suspend fun refineDay(
        ai: MealAi,
        profile: UserProfile,
        budget: EnergyBudget,
        planId: Long,
        dayIndex: Int,
        instruction: String,
    ): Result<String> {
        val plan = planDao.byId(planId) ?: return Result.failure(MealAiException("A terv nem található."))
        val startDate = LocalDate.ofEpochDay(plan.startEpochDay)
        val date = startDate.plusDays(dayIndex.toLong())

        val current = mealDao.mealsInRange(planId, date.toEpochDay(), date.toEpochDay())
        if (current.isEmpty()) return Result.failure(MealAiException("Ehhez a naphoz nincs étkezés."))

        val request = PlanRequest(
            profile = profile,
            budget = budget,
            days = 1,
            startDayIndex = dayIndex,
            totalDays = plan.dayCount,
            startWeekday = date.hungarianWeekday(),
        )

        val response = ai.refineDay(request, current.toAiDayJson(dayIndex), instruction)
            .getOrElse { return Result.failure(it) }

        mealDao.deleteDay(planId, dayIndex)
        insertDay(planId, startDate, response.day.copy(dayIndex = dayIndex))
        rebuildShoppingList(planId, plan.startEpochDay, plan.endEpochDay)

        return Result.success(response.explanation.ifBlank { "A napot frissítettem." })
    }

    private suspend fun insertDay(planId: Long, startDate: LocalDate, day: AiDay) {
        val date = startDate.plusDays(day.dayIndex.toLong())
        for (meal in day.meals) {
            val time = parseTime(meal.time)
            val mealId = mealDao.insert(
                MealEntity(
                    planId = planId,
                    dayIndex = day.dayIndex,
                    epochDay = date.toEpochDay(),
                    slot = meal.slot,
                    timeText = time.toString(),
                    scheduledAtMillis = date.atTime(time)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    name = meal.name,
                    description = meal.description,
                    prepMinutes = meal.prepMinutes,
                    servings = meal.servings,
                    nutrients = NutrientsColumns.from(meal.nutrition.toNutrients()),
                    recipeStepsJson = encodeStrings(meal.recipeSteps),
                    swapHint = meal.swapHint,
                )
            )
            mealDao.insertIngredients(
                meal.ingredients.map { ing ->
                    IngredientEntity(
                        mealId = mealId,
                        name = ing.name,
                        quantity = ing.quantity,
                        unit = ing.unit,
                        aisle = ing.aisle,
                        note = ing.note,
                        pantryStaple = ing.pantryStaple,
                    )
                }
            )
        }
    }

    fun observeShoppingList(planId: Long, from: Long, to: Long): Flow<List<ShoppingItemEntity>> =
        shoppingDao.observeRange(planId, from, to)

    suspend fun setShoppingChecked(id: Long, checked: Boolean) = shoppingDao.setChecked(id, checked)

    suspend fun uncheckShoppingList(planId: Long, from: Long, to: Long) =
        shoppingDao.uncheckAll(planId, from, to)

    /**
     * Újraépíti egy időszak bevásárlólistáját a hozzávalókból.
     * A már kipipált tételeket megőrizzük, hogy egy nap újratervezése ne nullázza a boltban a listát.
     */
    suspend fun rebuildShoppingList(planId: Long, fromEpochDay: Long, toEpochDay: Long) {
        val meals = mealDao.mealsInRange(planId, fromEpochDay, toEpochDay)
        val previouslyChecked = shoppingDao.checkedNames(planId, fromEpochDay, toEpochDay).toSet()

        val ingredients = meals.flatMap { mw ->
            mw.ingredients.map { ing ->
                AiIngredient(
                    name = ing.name,
                    quantity = ing.quantity,
                    unit = ing.unit,
                    aisle = ing.aisle,
                    note = ing.note,
                    pantryStaple = ing.pantryStaple,
                )
            }
        }

        shoppingDao.clearRange(planId, fromEpochDay, toEpochDay)
        shoppingDao.insertAll(
            ShoppingListBuilder.build(ingredients).map { item ->
                ShoppingItemEntity(
                    planId = planId,
                    fromEpochDay = fromEpochDay,
                    toEpochDay = toEpochDay,
                    name = item.name,
                    quantity = item.quantity,
                    unit = item.unit,
                    aisle = item.aisle.name,
                    notes = item.notes.joinToString("; "),
                    usedInMeals = item.usedInMeals,
                    checked = item.name in previouslyChecked,
                )
            }
        )
    }

    /**
     * Étkezési időpontok átállítása a meglévő terven.
     *
     * Helyi művelet: nem hívunk modellt, nem írjuk át a fogásokat, csak az időpontokat és
     * a hozzájuk tartozó emlékeztetőket. Egy ilyen kéréshez ("legyen a reggeli 9:45-kor")
     * nem szabad újratervezni — az percekbe és pénzbe kerülne, és elvenné a fogásokat,
     * amiket a felhasználó esetleg pont megtartani akart.
     *
     * @param dayIndexes ha üres, a terv minden napjára érvényes.
     * @return hány étkezés időpontja változott.
     */
    suspend fun setMealTimes(
        planId: Long,
        slotTimes: Map<String, LocalTime>,
        dayIndexes: List<Int> = emptyList(),
    ): Int {
        if (slotTimes.isEmpty()) return 0
        var changed = 0
        mealDao.allForPlan(planId).forEach { meal ->
            if (dayIndexes.isNotEmpty() && meal.dayIndex !in dayIndexes) return@forEach
            val newTime = slotTimes[meal.slot.trim().uppercase()] ?: return@forEach
            if (meal.timeText == newTime.toString()) return@forEach
            mealDao.update(meal.withTime(LocalDate.ofEpochDay(meal.epochDay), newTime))
            changed++
        }
        return changed
    }

    /**
     * Két nap étrendjének felcserélése. Szintén helyi művelet: az étkezések átkerülnek a
     * másik napra, a saját időpontjukat megtartva.
     */
    suspend fun swapDays(planId: Long, indexA: Int, indexB: Int): Boolean {
        if (indexA == indexB) return false
        val plan = planDao.byId(planId) ?: return false
        val start = LocalDate.ofEpochDay(plan.startEpochDay)
        val all = mealDao.allForPlan(planId)
        val fromA = all.filter { it.dayIndex == indexA }
        val fromB = all.filter { it.dayIndex == indexB }
        if (fromA.isEmpty() || fromB.isEmpty()) return false

        // Mindkét listát kiolvassuk, mielőtt bármit írnánk, különben a második mozgatás
        // már a megváltozott adatokat találná.
        moveMeals(fromA, start.plusDays(indexB.toLong()), indexB)
        moveMeals(fromB, start.plusDays(indexA.toLong()), indexA)
        return true
    }

    private suspend fun moveMeals(meals: List<MealEntity>, toDate: LocalDate, toDayIndex: Int) {
        meals.forEach { meal ->
            mealDao.update(
                meal.withTime(toDate, parseTime(meal.timeText)).copy(dayIndex = toDayIndex)
            )
        }
    }

    /** Egy tartomány listáját csak akkor gyártjuk le, ha még nem létezik — a pipák így megmaradnak. */
    suspend fun rebuildShoppingListIfMissing(planId: Long, fromEpochDay: Long, toEpochDay: Long) {
        if (shoppingDao.countInRange(planId, fromEpochDay, toEpochDay) == 0) {
            rebuildShoppingList(planId, fromEpochDay, toEpochDay)
        }
    }

    suspend fun deletePlan(planId: Long) = planDao.delete(planId)

    suspend fun mealsInRange(planId: Long, from: Long, to: Long) = mealDao.mealsInRange(planId, from, to)

    private fun parseTime(raw: String): LocalTime = runCatching { LocalTime.parse(raw.trim()) }
        .getOrElse { LocalTime.of(12, 0) }

    /** Egy étkezés áthelyezése adott napra és időpontra, az emlékeztető idejével együtt. */
    private fun MealEntity.withTime(date: LocalDate, time: LocalTime): MealEntity = copy(
        epochDay = date.toEpochDay(),
        timeText = time.toString(),
        scheduledAtMillis = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

    private fun List<MealWithIngredients>.toAiDayJson(dayIndex: Int): String = buildString {
        append("""{"day_index": $dayIndex, "meals": [""")
        this@toAiDayJson.forEachIndexed { index, mw ->
            if (index > 0) append(",")
            val n = mw.meal.nutrients
            append(
                """{"slot":"${mw.meal.slot}","time":"${mw.meal.timeText}",""" +
                    """"name":${quote(mw.meal.name)},"description":${quote(mw.meal.description)},""" +
                    """"prep_minutes":${mw.meal.prepMinutes},"servings":${mw.meal.servings},""" +
                    """"ingredients":[""" +
                    mw.ingredients.joinToString(",") { ing ->
                        """{"name":${quote(ing.name)},"quantity":${ing.quantity},"unit":${quote(ing.unit)},""" +
                            """"aisle":${quote(ing.aisle)},"pantry_staple":${ing.pantryStaple}}"""
                    } +
                    """],"nutrition":{"kcal":${n.kcal},"protein_g":${n.proteinG},""" +
                    """"carbs_g":${n.carbsG},"fat_g":${n.fatG},"fiber_g":${n.fiberG}}}"""
            )
        }
        append("]}")
    }

    private fun quote(value: String) = PlanParser.json.encodeToString(String.serializer(), value)

    private fun encodeStrings(values: List<String>): String =
        PlanParser.json.encodeToString(ListSerializer(String.serializer()), values)

    companion object {
        fun decodeStrings(json: String): List<String> = runCatching {
            PlanParser.json.decodeFromString(ListSerializer(String.serializer()), json)
        }.getOrDefault(emptyList())
    }
}

/** Magyar napnév a promptba (a hétvégére hosszabb receptek kerülhetnek). */
fun LocalDate.hungarianWeekday(): String = when (dayOfWeek.value) {
    1 -> "hétfő"; 2 -> "kedd"; 3 -> "szerda"; 4 -> "csütörtök"
    5 -> "péntek"; 6 -> "szombat"; else -> "vasárnap"
}
