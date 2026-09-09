package hu.mealpilot.app.data.ai

import hu.mealpilot.core.ai.AiDay
import hu.mealpilot.core.ai.AiDayResponse
import hu.mealpilot.core.ai.AiIngredient
import hu.mealpilot.core.ai.AiMeal
import hu.mealpilot.core.ai.AiNutrition
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.GenerationProgress
import hu.mealpilot.core.ai.MealAi
import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.ai.PlanRequest
import kotlin.math.roundToInt

/**
 * Tartalék tervező, ami internetkapcsolat nélkül is működik.
 *
 * Egy beépített receptbankból forgat étrendet, és a napi kalóriacélhoz méretezi az adagokat.
 * A szabad szöveges kéréseket nem érti, de a napi keretet és a makrókat pontosan tartja,
 * így az app a tervezőszolgáltatás elérhetetlensége esetén sem marad használhatatlan.
 */
class OfflineMealAi : MealAi {

    override val isConfigured: Boolean = true

    override suspend fun generatePlan(
        request: PlanRequest,
        onProgress: (GenerationProgress) -> Unit,
    ): Result<AiPlanResponse> = runCatching {
        val slots = MealSlot.forMealsPerDay(request.profile.mealsPerDay)
        val shares = slotShares(slots)
        val target = request.budget.target

        val days = (0 until request.days).map { offset ->
            val dayIndex = request.startDayIndex + offset
            onProgress(
                GenerationProgress(
                    stage = GenerationProgress.Stage.STREAMING,
                    currentChunk = offset,
                    totalChunks = request.days,
                    message = "${offset + 1}. nap összeállítása…",
                )
            )
            AiDay(
                dayIndex = dayIndex,
                title = "${dayIndex + 1}. nap",
                note = "Sablonból, a napi kerethez méretezve.",
                meals = slots.mapIndexed { slotIndex, slot ->
                    val bank = bankFor(slot)
                    val template = bank[(dayIndex * slots.size + slotIndex) % bank.size]
                    template.scaledTo(
                        targetKcal = target.kcal * shares[slotIndex],
                        slot = slot,
                        time = request.profile.mealTimes.getOrNull(slotIndex) ?: slot.defaultTime,
                    )
                },
            )
        }

        onProgress(
            GenerationProgress(
                stage = GenerationProgress.Stage.DONE,
                currentChunk = request.days,
                totalChunks = request.days,
                message = "Kész.",
            )
        )

        AiPlanResponse(
            planTitle = "Gyors étrend",
            summary = "Sablonokból épített terv: a napi kalória és a makrók a célodhoz vannak " +
                "méretezve. A szabad szöveges kéréseidet ez a változat nem veszi figyelembe.",
            days = days,
            coachNotes = listOf(
                "Igyál napi 2–3 liter folyadékot.",
                "A fehérjét oszd el egyenletesen a nap folyamán.",
            ),
        )
    }

    override suspend fun refineDay(
        request: PlanRequest,
        currentDayJson: String,
        instruction: String,
    ): Result<AiDayResponse> = Result.failure(
        MealAiException("Ez a változat nem tudja átírni a napokat. Próbáld újra, ha van internetkapcsolatod.")
    )

    /** A napi kalória elosztása az étkezések között. */
    private fun slotShares(slots: List<MealSlot>): List<Double> {
        val weights = slots.map { slot ->
            when (slot) {
                MealSlot.BREAKFAST -> 0.25
                MealSlot.LUNCH -> 0.35
                MealSlot.DINNER -> 0.30
                else -> 0.10
            }
        }
        val sum = weights.sum()
        return weights.map { it / sum }
    }

    private fun bankFor(slot: MealSlot): List<Template> = when (slot) {
        MealSlot.BREAKFAST -> BREAKFASTS
        MealSlot.LUNCH, MealSlot.DINNER -> MAINS
        else -> SNACKS
    }

    private data class Template(
        val name: String,
        val description: String,
        val prepMinutes: Int,
        val steps: List<String>,
        val ingredients: List<AiIngredient>,
        val nutrition: AiNutrition,
    ) {
        /** Az egész fogást egy szorzóval a kívánt kalóriaszintre méretezi. */
        fun scaledTo(targetKcal: Double, slot: MealSlot, time: String): AiMeal {
            val factor = if (nutrition.kcal <= 0) 1.0 else (targetKcal / nutrition.kcal).coerceIn(0.4, 2.5)
            return AiMeal(
                slot = slot.name,
                time = time,
                name = name,
                description = description,
                prepMinutes = prepMinutes,
                servings = 1.0,
                recipeSteps = steps,
                ingredients = ingredients.map { ing ->
                    if (ing.pantryStaple) ing
                    else ing.copy(quantity = round1(ing.quantity * factor))
                },
                nutrition = AiNutrition(
                    kcal = round1(nutrition.kcal * factor),
                    proteinG = round1(nutrition.proteinG * factor),
                    carbsG = round1(nutrition.carbsG * factor),
                    fatG = round1(nutrition.fatG * factor),
                    fiberG = round1(nutrition.fiberG * factor),
                    sugarG = round1(nutrition.sugarG * factor),
                    saturatedFatG = round1(nutrition.saturatedFatG * factor),
                    sodiumMg = round1(nutrition.sodiumMg * factor),
                ),
                swapHint = "",
            )
        }

        private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
    }

    private companion object {

        fun ing(name: String, qty: Double, unit: String, aisle: String, staple: Boolean = false) =
            AiIngredient(name = name, quantity = qty, unit = unit, aisle = aisle, pantryStaple = staple)

        val BREAKFASTS = listOf(
            Template(
                "Túrós-zabpelyhes tál bogyós gyümölccsel",
                "Gyors, magas fehérjetartalmú reggeli.",
                5,
                listOf("Keverd össze a túrót a zabpehellyel.", "Tedd rá a gyümölcsöt és a magokat."),
                listOf(
                    ing("sovány túró", 200.0, "g", "TEJTERMEK"),
                    ing("zabpehely", 40.0, "g", "SZARAZARU"),
                    ing("áfonya", 80.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("dió", 15.0, "g", "SZARAZARU"),
                ),
                AiNutrition(kcal = 480.0, proteinG = 38.0, carbsG = 45.0, fatG = 15.0, fiberG = 7.0, sugarG = 12.0),
            ),
            Template(
                "Rántotta teljes kiőrlésű pirítóssal",
                "Klasszikus, laktató reggeli.",
                10,
                listOf("Süsd meg a tojásokat kevés olajon.", "Pirítsd meg a kenyeret, tedd mellé a zöldséget."),
                listOf(
                    ing("tojás", 3.0, "db", "TEJTERMEK"),
                    ing("teljes kiőrlésű kenyér", 60.0, "g", "PEKARU"),
                    ing("paradicsom", 100.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("olívaolaj", 5.0, "ml", "FUSZER", staple = true),
                ),
                AiNutrition(kcal = 460.0, proteinG = 27.0, carbsG = 34.0, fatG = 22.0, fiberG = 6.0, sugarG = 5.0),
            ),
            Template(
                "Görög joghurtos smoothie tál",
                "Reggeli, ami előre elkészíthető.",
                5,
                listOf("Turmixold össze a joghurtot a banánnal.", "Szórd meg maggal és zabbal."),
                listOf(
                    ing("görög joghurt", 250.0, "g", "TEJTERMEK"),
                    ing("banán", 1.0, "db", "ZOLDSEG_GYUMOLCS"),
                    ing("zabpehely", 30.0, "g", "SZARAZARU"),
                    ing("chia mag", 10.0, "g", "SZARAZARU"),
                ),
                AiNutrition(kcal = 450.0, proteinG = 26.0, carbsG = 52.0, fatG = 14.0, fiberG = 8.0, sugarG = 20.0),
            ),
        )

        val MAINS = listOf(
            Template(
                "Grillezett csirkemell párolt rizzsel és salátával",
                "Egyszerű, kiszámítható alap fogás.",
                25,
                listOf("Fűszerezd és süsd meg a csirkemellet.", "Főzz rizst.", "Készíts hozzá salátát."),
                listOf(
                    ing("csirkemell", 180.0, "g", "HUS_HAL"),
                    ing("barna rizs", 70.0, "g", "SZARAZARU"),
                    ing("vegyes saláta", 120.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("olívaolaj", 10.0, "ml", "FUSZER", staple = true),
                ),
                AiNutrition(kcal = 620.0, proteinG = 50.0, carbsG = 60.0, fatG = 17.0, fiberG = 6.0, sugarG = 4.0),
            ),
            Template(
                "Sült lazac édesburgonyával és brokkolival",
                "Omega-3-ban gazdag főétel.",
                30,
                listOf("Süsd a lazacot 180 fokon 15 percig.", "Süsd meg az édesburgonyát.", "Párold a brokkolit."),
                listOf(
                    ing("lazacfilé", 160.0, "g", "HUS_HAL"),
                    ing("édesburgonya", 250.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("brokkoli", 200.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("olívaolaj", 10.0, "ml", "FUSZER", staple = true),
                ),
                AiNutrition(kcal = 640.0, proteinG = 40.0, carbsG = 55.0, fatG = 27.0, fiberG = 9.0, sugarG = 12.0),
            ),
            Template(
                "Bolognai lencseragu teljes kiőrlésű tésztával",
                "Növényi fehérje, sok rost.",
                30,
                listOf("Párold meg a zöldségeket.", "Add hozzá a lencsét és a paradicsomot.", "Főzd ki a tésztát."),
                listOf(
                    ing("vörös lencse", 90.0, "g", "SZARAZARU"),
                    ing("teljes kiőrlésű tészta", 80.0, "g", "SZARAZARU"),
                    ing("paradicsomkonzerv", 200.0, "g", "SZARAZARU"),
                    ing("vöröshagyma", 80.0, "g", "ZOLDSEG_GYUMOLCS"),
                ),
                AiNutrition(kcal = 610.0, proteinG = 32.0, carbsG = 100.0, fatG = 8.0, fiberG = 18.0, sugarG = 12.0),
            ),
            Template(
                "Marhapörkölt párolt zöldségekkel",
                "Hétvégi, laktató fogás.",
                75,
                listOf("Pirítsd meg a hagymát.", "Főzd puhára a húst.", "Párold mellé a zöldséget."),
                listOf(
                    ing("marhalábszár", 180.0, "g", "HUS_HAL"),
                    ing("burgonya", 200.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("vöröshagyma", 100.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("paprika", 100.0, "g", "ZOLDSEG_GYUMOLCS"),
                ),
                AiNutrition(kcal = 650.0, proteinG = 45.0, carbsG = 48.0, fatG = 28.0, fiberG = 7.0, sugarG = 8.0),
            ),
            Template(
                "Csirkés-zöldséges wok basmati rizzsel",
                "Egy serpenyős, 20 perces vacsora.",
                20,
                listOf("Pirítsd a csirkét.", "Dobd hozzá a zöldségeket.", "Ízesítsd szójaszósszal."),
                listOf(
                    ing("csirkecomb filé", 170.0, "g", "HUS_HAL"),
                    ing("wok zöldségkeverék", 250.0, "g", "FAGYASZTOTT"),
                    ing("basmati rizs", 70.0, "g", "SZARAZARU"),
                    ing("szójaszósz", 15.0, "ml", "FUSZER", staple = true),
                ),
                AiNutrition(kcal = 630.0, proteinG = 43.0, carbsG = 68.0, fatG = 18.0, fiberG = 8.0, sugarG = 9.0),
            ),
        )

        val SNACKS = listOf(
            Template(
                "Görög joghurt dióval",
                "Gyors fehérjeforrás két étkezés között.",
                2,
                listOf("Keverd össze."),
                listOf(
                    ing("görög joghurt", 150.0, "g", "TEJTERMEK"),
                    ing("dió", 15.0, "g", "SZARAZARU"),
                ),
                AiNutrition(kcal = 220.0, proteinG = 16.0, carbsG = 9.0, fatG = 13.0, fiberG = 1.0, sugarG = 7.0),
            ),
            Template(
                "Alma mogyoróvajjal",
                "Rost és jó zsírok.",
                2,
                listOf("Szeleteld fel az almát, kend meg."),
                listOf(
                    ing("alma", 1.0, "db", "ZOLDSEG_GYUMOLCS"),
                    ing("mogyoróvaj", 20.0, "g", "SZARAZARU"),
                ),
                AiNutrition(kcal = 220.0, proteinG = 6.0, carbsG = 25.0, fatG = 11.0, fiberG = 5.0, sugarG = 18.0),
            ),
            Template(
                "Sárgarépa hummusszal",
                "Ropogós, alacsony kalóriájú nassolnivaló.",
                3,
                listOf("Vágd a répát csíkokra.", "Mártsd a hummuszba."),
                listOf(
                    ing("sárgarépa", 150.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("hummusz", 60.0, "g", "SZARAZARU"),
                ),
                AiNutrition(kcal = 210.0, proteinG = 7.0, carbsG = 24.0, fatG = 9.0, fiberG = 7.0, sugarG = 8.0),
            ),
        )
    }
}
