package hu.mealpilot.app.data.repo

import hu.mealpilot.app.BuildConfig
import hu.mealpilot.app.data.local.AchievementDao
import hu.mealpilot.app.data.local.FavoriteDao
import hu.mealpilot.app.data.local.MealDao
import hu.mealpilot.app.data.local.MealLogDao
import hu.mealpilot.app.data.local.NutrientsColumns
import hu.mealpilot.app.data.local.PlanDao
import hu.mealpilot.app.data.local.WeightLogDao
import hu.mealpilot.app.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

/**
 * „Add ki az adataimat." — minden, amit az app a felhasználóról tárol, egy JSON-ban.
 *
 * MIÉRT KELL. A jogi oldalak között ott az adattörlés, a kivitel viszont hiányzott:
 * a felhasználó nem tudta megnézni és magával vinni, amit róla tárolunk. Ez nem csak
 * jogi kérdés — bizalmi is, és az egyetlen módja annak, hogy valaki a saját naplóját
 * máshol is használhassa.
 *
 * MI NINCS BENNE, ÉS MIÉRT SZERKEZETILEG NINCS. Titok — saját API-kulcs, háttérszolgálati
 * jogosultság — nem kerülhet egy megosztható fájlba: onnan egy félreküldött e-maillel
 * kikerülne. Ez az osztály ezért NEM FÉR HOZZÁ a kulcstárolóhoz: csak az adatbázis
 * DAO-it és a profilt kapja meg. A kihagyás így nem egy elfelejthető szűrés kérdése,
 * hanem annak, hogy a titok nincs is kéznél.
 */
class ExportRepository(
    private val settings: SettingsRepository,
    private val planDao: PlanDao,
    private val mealDao: MealDao,
    private val mealLogDao: MealLogDao,
    private val weightLogDao: WeightLogDao,
    private val favoriteDao: FavoriteDao,
    private val achievementDao: AchievementDao,
) {

    /** Az egész napló egyetlen, olvasható JSON szövegben. */
    suspend fun buildJson(nowMillis: Long = System.currentTimeMillis()): String =
        withContext(Dispatchers.Default) { PRETTY.encodeToString(JsonObject.serializer(), build(nowMillis)) }

    /** A javasolt fájlnév: dátummal, hogy több kivitel ne írja felül egymást. */
    fun fileName(nowMillis: Long = System.currentTimeMillis()): String {
        val day = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return "mealpilot-adatok-$day.json"
    }

    private suspend fun build(nowMillis: Long): JsonObject {
        // MINDEN adatbázis-olvasás ITT történik, a JSON építése előtt. A
        // `buildJsonObject` lambdája nem `suspend`, tehát egy DAO-hívás odabent
        // fordítási hiba — és ez pont az a hiba, amit csak a fordító vesz észre.
        val profile = settings.currentProfile()
        val plans = planDao.all()
        val mealsByPlan = plans.associate { plan ->
            plan.id to mealDao.mealsInRange(
                plan.id,
                plan.startEpochDay,
                plan.startEpochDay + plan.dayCount - 1,
            )
        }
        val weightLogs = weightLogDao.all()
        val mealLogs = mealLogDao.all()
        val favorites = favoriteDao.all()
        val achievements = achievementDao.all()

        return buildJsonObject {
            put("format", FORMAT)
            put("app_version", BuildConfig.VERSION_NAME)
            put("exported_at", Instant.ofEpochMilli(nowMillis).toString())

            putJsonObject("profile") {
                put("name", profile.name)
                put("sex", profile.sex.name)
                put("age_years", profile.ageYears)
                put("height_cm", profile.heightCm)
                put("weight_kg", profile.weightKg)
                profile.bodyFatPercent?.let { put("body_fat_percent", it) }
                profile.targetWeightKg?.let { put("target_weight_kg", it) }
                put("target_rate_kg_per_week", profile.targetRateKgPerWeek)
                put("activity_level", profile.activityLevel.name)
                put("diet_style", profile.dietStyle.name)
                put("macro_preset", profile.macroPreset.name)
                put("meals_per_day", profile.mealsPerDay)
                put("batch_cooking", profile.batchCooking)
                put("preferences", profile.preferences)
                putJsonArray("restrictions") { profile.restrictions.forEach { add(it.name) } }
                putJsonArray("meal_times") { profile.mealTimes.forEach { add(it) } }
            }

            putJsonArray("weight_logs") {
                weightLogs.forEach { log ->
                    add(buildJsonObject {
                        put("date", LocalDate.ofEpochDay(log.epochDay).toString())
                        put("weight_kg", log.weightKg)
                        log.bodyFatPercent?.let { put("body_fat_percent", it) }
                        put("note", log.note)
                    })
                }
            }

            putJsonArray("meal_logs") {
                mealLogs.forEach { log ->
                    add(buildJsonObject {
                        put("date", LocalDate.ofEpochDay(log.epochDay).toString())
                        put("status", log.status)
                        put("name", log.name)
                        put("note", log.note)
                        put("nutrition", nutrition(log.nutrients))
                    })
                }
            }

            putJsonArray("plans") {
                plans.forEach { plan ->
                    add(buildJsonObject {
                        put("title", plan.title)
                        put("summary", plan.summary)
                        put("start_date", LocalDate.ofEpochDay(plan.startEpochDay).toString())
                        put("day_count", plan.dayCount)
                        put("active", plan.isActive)
                        put("request_text", plan.requestText)
                        put("target_kcal", plan.targetKcal)
                        putJsonArray("meals") {
                            mealsByPlan.getValue(plan.id).forEach { meal ->
                                add(buildJsonObject {
                                    put("date", LocalDate.ofEpochDay(meal.meal.epochDay).toString())
                                    put("slot", meal.meal.slot)
                                    put("time", meal.meal.timeText)
                                    put("name", meal.meal.name)
                                    put("description", meal.meal.description)
                                    put("prep_minutes", meal.meal.prepMinutes)
                                    put("nutrition", nutrition(meal.meal.nutrients))
                                    put("recipe_steps", strings(meal.meal.recipeStepsJson))
                                    putJsonArray("ingredients") {
                                        meal.ingredients.forEach { ingredient ->
                                            add(buildJsonObject {
                                                put("name", ingredient.name)
                                                put("quantity", ingredient.quantity)
                                                put("unit", ingredient.unit)
                                                put("aisle", ingredient.aisle)
                                            })
                                        }
                                    }
                                })
                            }
                        }
                    })
                }
            }

            putJsonArray("favorites") {
                favorites.forEach { entry ->
                    add(buildJsonObject {
                        put("name", entry.favorite.name)
                        put("slot", entry.favorite.slot)
                        put("description", entry.favorite.description)
                        put("prep_minutes", entry.favorite.prepMinutes)
                        put("nutrition", nutrition(entry.favorite.nutrients))
                        put("recipe_steps", strings(entry.favorite.recipeStepsJson))
                        putJsonArray("ingredients") {
                            entry.ingredients.forEach { ingredient ->
                                add(buildJsonObject {
                                    put("name", ingredient.name)
                                    put("quantity", ingredient.quantity)
                                    put("unit", ingredient.unit)
                                })
                            }
                        }
                    })
                }
            }

            putJsonArray("achievements") {
                achievements.forEach { unlocked ->
                    add(buildJsonObject {
                        put("key", unlocked.key)
                        put("unlocked_at", Instant.ofEpochMilli(unlocked.unlockedAtMillis).toString())
                    })
                }
            }
        }
    }

    private fun nutrition(n: NutrientsColumns): JsonObject = buildJsonObject {
        put("kcal", n.kcal)
        put("protein_g", n.proteinG)
        put("carbs_g", n.carbsG)
        put("fat_g", n.fatG)
        put("fiber_g", n.fiberG)
        put("sugar_g", n.sugarG)
    }

    private fun strings(json: String): JsonArray =
        buildJsonArray { PlanRepository.decodeStrings(json).forEach { add(it) } }

    private companion object {
        /** A formátum verziója: ha a szerkezet változik, erről lehet felismerni. */
        const val FORMAT = "mealpilot-export-1"
        val PRETTY = Json { prettyPrint = true; encodeDefaults = true }
    }
}
