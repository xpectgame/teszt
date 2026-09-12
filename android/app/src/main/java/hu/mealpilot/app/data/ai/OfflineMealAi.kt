package hu.mealpilot.app.data.ai

import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.app.R
import hu.mealpilot.core.i18n.Text
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.ai.AiDay
import hu.mealpilot.core.ai.AiDayResponse
import hu.mealpilot.core.ai.AiIngredient
import hu.mealpilot.core.ai.AiMeal
import hu.mealpilot.core.ai.AiNutrition
import hu.mealpilot.core.ai.AiChatResponse
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.ChatContext
import hu.mealpilot.core.ai.ChatTurn
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
class OfflineMealAi(
    /** Lásd [StreamingMealAi]: a nyelv függvény, mert a felhasználó menet közben vált. */
    private val languageProvider: () -> AppLanguage,
    private val strings: AppStrings,
) : MealAi {

    private val language: AppLanguage get() = languageProvider()

    override val isConfigured: Boolean = true

    override suspend fun generatePlan(
        request: PlanRequest,
        onProgress: (GenerationProgress) -> Unit,
        onChunk: suspend (AiPlanResponse) -> Unit,
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
                    message = strings[R.string.progress_day, offset + 1],
                )
            )
            AiDay(
                dayIndex = dayIndex,
                title = strings[R.string.offline_day_title, dayIndex + 1],
                note = strings[R.string.offline_day_note],
                meals = slots.mapIndexed { slotIndex, slot ->
                    val bank = bankFor(slot)
                    val template = bank[(dayIndex * slots.size + slotIndex) % bank.size]
                    template.scaledTo(
                        targetKcal = target.kcal * shares[slotIndex],
                        slot = slot,
                        time = request.profile.mealTimes.getOrNull(slotIndex) ?: slot.defaultTime,
                        language = language,
                    )
                },
            )
        }

        onProgress(
            GenerationProgress(
                stage = GenerationProgress.Stage.DONE,
                currentChunk = request.days,
                totalChunks = request.days,
                message = strings[R.string.progress_done],
            )
        )

        val result = AiPlanResponse(
            planTitle = strings[R.string.offline_plan_title],
            summary = strings[R.string.offline_plan_summary],
            days = days,
            coachNotes = listOf(
                strings[R.string.offline_tip_water],
                strings[R.string.offline_tip_protein],
            ),
        )
        onChunk(result)
        result
    }

    override suspend fun chat(
        context: ChatContext,
        history: List<ChatTurn>,
        message: String,
    ): Result<AiChatResponse> = Result.failure(
        MealAiException(strings[R.string.offline_chat_needs_network])
    )

    override suspend fun refineDay(
        request: PlanRequest,
        currentDayJson: String,
        instruction: String,
    ): Result<AiDayResponse> = Result.failure(
        MealAiException(strings[R.string.offline_cannot_refine])
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

    /** Kétnyelvű hozzávaló; a mennyiség és a polc nyelvfüggetlen. */
    private data class Ingredient(
        val name: Text,
        val quantity: Double,
        val unit: String,
        val aisle: String,
        val pantryStaple: Boolean = false,
    ) {
        fun toAi(language: AppLanguage, factor: Double) = AiIngredient(
            name = name.get(language),
            // A kamrai alapanyagot (olaj, só) nem méretezzük: egy 1,7-szeres kanál olaj
            // nem mond semmit, csak zajt visz a bevásárlólistára.
            quantity = if (pantryStaple) quantity else (quantity * factor * 10).roundToInt() / 10.0,
            unit = unit,
            aisle = aisle,
            pantryStaple = pantryStaple,
        )
    }

    /**
     * Egy sablonfogás, mindkét nyelven.
     *
     * A receptbank tartalom, nem felirat: nem Android erőforrásból jön, mert a
     * hozzávalók neve a bevásárlólistára és a naplóba is bekerül, és ott a TERV
     * nyelvén kell állnia — nem azon, amit a telefon éppen mutat.
     */
    private data class Template(
        val name: Text,
        val description: Text,
        val prepMinutes: Int,
        val steps: List<Text>,
        val ingredients: List<Ingredient>,
        val nutrition: AiNutrition,
    ) {
        /** Az egész fogást egy szorzóval a kívánt kalóriaszintre méretezi. */
        fun scaledTo(targetKcal: Double, slot: MealSlot, time: String, language: AppLanguage): AiMeal {
            val factor = if (nutrition.kcal <= 0) 1.0 else (targetKcal / nutrition.kcal).coerceIn(0.4, 2.5)
            return AiMeal(
                slot = slot.name,
                time = time,
                name = name.get(language),
                description = description.get(language),
                prepMinutes = prepMinutes,
                servings = 1.0,
                recipeSteps = steps.map { it.get(language) },
                ingredients = ingredients.map { it.toAi(language, factor) },
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

        fun ing(
            hungarian: String,
            english: String,
            qty: Double,
            unit: String,
            aisle: String,
            staple: Boolean = false,
        ) = Ingredient(Text(hungarian, english), qty, unit, aisle, staple)

        fun t(hungarian: String, english: String) = Text(hungarian, english)

        val BREAKFASTS = listOf(
            Template(
                t("Túrós-zabpelyhes tál bogyós gyümölccsel", "Quark and oat bowl with berries"),
                t("Gyors, magas fehérjetartalmú reggeli.", "A quick, high-protein breakfast."),
                5,
                listOf(
                    t("Keverd össze a túrót a zabpehellyel.", "Mix the quark with the oats."),
                    t("Tedd rá a gyümölcsöt és a magokat.", "Top with the fruit and the nuts."),
                ),
                listOf(
                    ing("sovány túró", "low-fat quark", 200.0, "g", "TEJTERMEK"),
                    ing("zabpehely", "rolled oats", 40.0, "g", "SZARAZARU"),
                    ing("áfonya", "blueberries", 80.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("dió", "walnuts", 15.0, "g", "SZARAZARU"),
                ),
                AiNutrition(kcal = 480.0, proteinG = 38.0, carbsG = 45.0, fatG = 15.0, fiberG = 7.0, sugarG = 12.0),
            ),
            Template(
                t("Rántotta teljes kiőrlésű pirítóssal", "Scrambled eggs with wholemeal toast"),
                t("Klasszikus, laktató reggeli.", "A classic, filling breakfast."),
                10,
                listOf(
                    t("Süsd meg a tojásokat kevés olajon.", "Scramble the eggs in a little oil."),
                    t("Pirítsd meg a kenyeret, tedd mellé a zöldséget.", "Toast the bread and serve the veg alongside."),
                ),
                listOf(
                    ing("tojás", "eggs", 3.0, "db", "TEJTERMEK"),
                    ing("teljes kiőrlésű kenyér", "wholemeal bread", 60.0, "g", "PEKARU"),
                    ing("paradicsom", "tomato", 100.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("olívaolaj", "olive oil", 5.0, "ml", "FUSZER", staple = true),
                ),
                AiNutrition(kcal = 460.0, proteinG = 27.0, carbsG = 34.0, fatG = 22.0, fiberG = 6.0, sugarG = 5.0),
            ),
            Template(
                t("Görög joghurtos smoothie tál", "Greek yoghurt smoothie bowl"),
                t("Reggeli, ami előre elkészíthető.", "A breakfast you can make ahead."),
                5,
                listOf(
                    t("Turmixold össze a joghurtot a banánnal.", "Blend the yoghurt with the banana."),
                    t("Szórd meg maggal és zabbal.", "Scatter over the seeds and oats."),
                ),
                listOf(
                    ing("görög joghurt", "Greek yoghurt", 250.0, "g", "TEJTERMEK"),
                    ing("banán", "banana", 1.0, "db", "ZOLDSEG_GYUMOLCS"),
                    ing("zabpehely", "rolled oats", 30.0, "g", "SZARAZARU"),
                    ing("chia mag", "chia seeds", 10.0, "g", "SZARAZARU"),
                ),
                AiNutrition(kcal = 450.0, proteinG = 26.0, carbsG = 52.0, fatG = 14.0, fiberG = 8.0, sugarG = 20.0),
            ),
        )

        val MAINS = listOf(
            Template(
                t("Grillezett csirkemell párolt rizzsel és salátával", "Grilled chicken breast with rice and salad"),
                t("Egyszerű, kiszámítható alap fogás.", "A simple, dependable staple."),
                25,
                listOf(
                    t("Fűszerezd és süsd meg a csirkemellet.", "Season and grill the chicken breast."),
                    t("Főzz rizst.", "Cook the rice."),
                    t("Készíts hozzá salátát.", "Put a salad together on the side."),
                ),
                listOf(
                    ing("csirkemell", "chicken breast", 180.0, "g", "HUS_HAL"),
                    ing("barna rizs", "brown rice", 70.0, "g", "SZARAZARU"),
                    ing("vegyes saláta", "mixed salad leaves", 120.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("olívaolaj", "olive oil", 10.0, "ml", "FUSZER", staple = true),
                ),
                AiNutrition(kcal = 620.0, proteinG = 50.0, carbsG = 60.0, fatG = 17.0, fiberG = 6.0, sugarG = 4.0),
            ),
            Template(
                t("Sült lazac édesburgonyával és brokkolival", "Baked salmon with sweet potato and broccoli"),
                t("Omega-3-ban gazdag főétel.", "A main course rich in omega-3."),
                30,
                listOf(
                    t("Süsd a lazacot 180 fokon 15 percig.", "Bake the salmon at 180°C for 15 minutes."),
                    t("Süsd meg az édesburgonyát.", "Roast the sweet potato."),
                    t("Párold a brokkolit.", "Steam the broccoli."),
                ),
                listOf(
                    ing("lazacfilé", "salmon fillet", 160.0, "g", "HUS_HAL"),
                    ing("édesburgonya", "sweet potato", 250.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("brokkoli", "broccoli", 200.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("olívaolaj", "olive oil", 10.0, "ml", "FUSZER", staple = true),
                ),
                AiNutrition(kcal = 640.0, proteinG = 40.0, carbsG = 55.0, fatG = 27.0, fiberG = 9.0, sugarG = 12.0),
            ),
            Template(
                t("Bolognai lencseragu teljes kiőrlésű tésztával", "Lentil bolognese with wholemeal pasta"),
                t("Növényi fehérje, sok rost.", "Plant protein and plenty of fibre."),
                30,
                listOf(
                    t("Párold meg a zöldségeket.", "Soften the vegetables."),
                    t("Add hozzá a lencsét és a paradicsomot.", "Add the lentils and the tomatoes."),
                    t("Főzd ki a tésztát.", "Cook the pasta."),
                ),
                listOf(
                    ing("vörös lencse", "red lentils", 90.0, "g", "SZARAZARU"),
                    ing("teljes kiőrlésű tészta", "wholemeal pasta", 80.0, "g", "SZARAZARU"),
                    ing("paradicsomkonzerv", "tinned tomatoes", 200.0, "g", "SZARAZARU"),
                    ing("vöröshagyma", "onion", 80.0, "g", "ZOLDSEG_GYUMOLCS"),
                ),
                AiNutrition(kcal = 610.0, proteinG = 32.0, carbsG = 100.0, fatG = 8.0, fiberG = 18.0, sugarG = 12.0),
            ),
            Template(
                t("Marhapörkölt párolt zöldségekkel", "Beef stew with braised vegetables"),
                t("Hétvégi, laktató fogás.", "A hearty weekend dish."),
                75,
                listOf(
                    t("Pirítsd meg a hagymát.", "Brown the onion."),
                    t("Főzd puhára a húst.", "Simmer the beef until tender."),
                    t("Párold mellé a zöldséget.", "Braise the vegetables alongside."),
                ),
                listOf(
                    ing("marhalábszár", "beef shin", 180.0, "g", "HUS_HAL"),
                    ing("burgonya", "potato", 200.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("vöröshagyma", "onion", 100.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("paprika", "bell pepper", 100.0, "g", "ZOLDSEG_GYUMOLCS"),
                ),
                AiNutrition(kcal = 650.0, proteinG = 45.0, carbsG = 48.0, fatG = 28.0, fiberG = 7.0, sugarG = 8.0),
            ),
            Template(
                t("Csirkés-zöldséges wok basmati rizzsel", "Chicken and vegetable stir-fry with basmati rice"),
                t("Egy serpenyős, 20 perces vacsora.", "A one-pan dinner in 20 minutes."),
                20,
                listOf(
                    t("Pirítsd a csirkét.", "Sear the chicken."),
                    t("Dobd hozzá a zöldségeket.", "Throw in the vegetables."),
                    t("Ízesítsd szójaszósszal.", "Season with soy sauce."),
                ),
                listOf(
                    ing("csirkecomb filé", "chicken thigh fillet", 170.0, "g", "HUS_HAL"),
                    ing("wok zöldségkeverék", "stir-fry vegetable mix", 250.0, "g", "FAGYASZTOTT"),
                    ing("basmati rizs", "basmati rice", 70.0, "g", "SZARAZARU"),
                    ing("szójaszósz", "soy sauce", 15.0, "ml", "FUSZER", staple = true),
                ),
                AiNutrition(kcal = 630.0, proteinG = 43.0, carbsG = 68.0, fatG = 18.0, fiberG = 8.0, sugarG = 9.0),
            ),
        )

        val SNACKS = listOf(
            Template(
                t("Görög joghurt dióval", "Greek yoghurt with walnuts"),
                t("Gyors fehérjeforrás két étkezés között.", "A quick protein hit between meals."),
                2,
                listOf(t("Keverd össze.", "Stir together.")),
                listOf(
                    ing("görög joghurt", "Greek yoghurt", 150.0, "g", "TEJTERMEK"),
                    ing("dió", "walnuts", 15.0, "g", "SZARAZARU"),
                ),
                AiNutrition(kcal = 220.0, proteinG = 16.0, carbsG = 9.0, fatG = 13.0, fiberG = 1.0, sugarG = 7.0),
            ),
            Template(
                t("Alma mogyoróvajjal", "Apple with peanut butter"),
                t("Rost és jó zsírok.", "Fibre and good fats."),
                2,
                listOf(t("Szeleteld fel az almát, kend meg.", "Slice the apple and spread it.")),
                listOf(
                    ing("alma", "apple", 1.0, "db", "ZOLDSEG_GYUMOLCS"),
                    ing("mogyoróvaj", "peanut butter", 20.0, "g", "SZARAZARU"),
                ),
                AiNutrition(kcal = 220.0, proteinG = 6.0, carbsG = 25.0, fatG = 11.0, fiberG = 5.0, sugarG = 18.0),
            ),
            Template(
                t("Sárgarépa hummusszal", "Carrot sticks with hummus"),
                t("Ropogós, alacsony kalóriájú nassolnivaló.", "A crunchy, low-calorie snack."),
                3,
                listOf(
                    t("Vágd a répát csíkokra.", "Cut the carrots into sticks."),
                    t("Mártsd a hummuszba.", "Dip them in the hummus."),
                ),
                listOf(
                    ing("sárgarépa", "carrot", 150.0, "g", "ZOLDSEG_GYUMOLCS"),
                    ing("hummusz", "hummus", 60.0, "g", "SZARAZARU"),
                ),
                AiNutrition(kcal = 210.0, proteinG = 7.0, carbsG = 24.0, fatG = 9.0, fiberG = 7.0, sugarG = 8.0),
            ),
        )
    }
}
