package hu.mealpilot.app.data.repo

import hu.mealpilot.app.data.local.FavoriteDao
import hu.mealpilot.app.data.local.FavoriteIngredientEntity
import hu.mealpilot.app.data.local.FavoriteMealEntity
import hu.mealpilot.app.data.local.FavoriteWithIngredients
import hu.mealpilot.app.data.local.MealWithIngredients
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Kedvencek: fogások, amiket a felhasználó újra akar látni.
 *
 * A kedvenc MÁSOLAT, nem hivatkozás a terv fogására. A tervek törölhetők, a fogásaik a
 * tervvel együtt szűnnek meg — egy kedvenc, ami a terv törlésekor eltűnik, nem kedvenc.
 * Ezért kerül át a recept és a hozzávalólista is.
 *
 * Az azonosság a NÉVEN áll (kisbetűsen, levágva), nem a fogás azonosítóján. A „Shakshuka"
 * hétfőn és pénteken két külön `meals` sor, a felhasználónak viszont ugyanaz az étel: ha
 * az azonosító döntene, a szív ikon pénteken üres lenne, és a lista tele lenne
 * ismétlésekkel.
 */
class FavoriteRepository(private val dao: FavoriteDao) {

    fun observeFavorites(): Flow<List<FavoriteWithIngredients>> = dao.observeAll()

    /** A megjelölt fogások kulcsai — ennyi kell a szív ikon állapotához. */
    fun observeKeys(): Flow<Set<String>> = dao.observeKeys().map { it.toSet() }

    suspend fun isFavorite(name: String): Boolean = dao.idOf(key(name)) != null

    suspend fun byId(id: Long): FavoriteWithIngredients? = dao.byId(id)

    suspend fun count(): Int = dao.count()

    /**
     * A legutóbb megjelölt kedvencek neve, a tervezőnek.
     *
     * Korlátozva: a promptba nem mehet be nyitott számú sor, és a régi kedvenc kevésbé
     * mond valamit a mai ízlésről, mint a tegnapi.
     */
    suspend fun namesForPlanning(limit: Int = PLANNING_LIMIT): List<String> =
        dao.recentNames(limit)

    /**
     * Megjelöli vagy leveszi a jelölést. Az új állapotot adja vissza (igaz = kedvenc lett).
     *
     * Szándékosan kapcsoló: a felületen egyetlen szív van, és a felhasználónak nem kell
     * tudnia, épp melyik művelet fut.
     */
    suspend fun toggle(data: MealWithIngredients, nowMillis: Long): Boolean {
        val existing = dao.idOf(key(data.meal.name))
        if (existing != null) {
            dao.deleteById(existing)
            return false
        }
        val meal = data.meal
        val id = dao.insert(
            FavoriteMealEntity(
                name = meal.name.trim(),
                nameKey = key(meal.name),
                description = meal.description,
                slot = meal.slot,
                prepMinutes = meal.prepMinutes,
                servings = meal.servings,
                nutrients = meal.nutrients,
                recipeStepsJson = meal.recipeStepsJson,
                addedAtMillis = nowMillis,
            )
        )
        // Az IGNORE stratégia -1-et ad, ha közben más szálon már bekerült ugyanaz a név.
        // Ilyenkor a jelölés attól még érvényes — csak nekünk nincs mit beszúrnunk.
        if (id <= 0) return true
        dao.insertIngredients(
            data.ingredients.map {
                FavoriteIngredientEntity(
                    favoriteId = id,
                    name = it.name,
                    quantity = it.quantity,
                    unit = it.unit,
                    aisle = it.aisle,
                    note = it.note,
                    pantryStaple = it.pantryStaple,
                )
            }
        )
        return true
    }

    suspend fun remove(id: Long) = dao.deleteById(id)

    companion object {
        /** Ennyi kedvenc neve mehet be egy tervezési kérésbe. */
        const val PLANNING_LIMIT = 25

        /**
         * A név normalizált alakja. Kisbetűs és levágott, mert a modell ugyanazt a
         * fogást írhatja „Shakshuka"-ként és „shakshuka "-ként is.
         */
        fun key(name: String): String = name.trim().lowercase()
    }
}
