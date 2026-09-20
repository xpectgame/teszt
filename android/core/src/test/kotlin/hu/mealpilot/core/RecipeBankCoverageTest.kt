package hu.mealpilot.core

import hu.mealpilot.core.ai.MealSlot
import hu.mealpilot.core.ai.RecipeBank
import hu.mealpilot.core.ai.RecipeTemplate
import hu.mealpilot.core.ai.RestrictionChecker
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.i18n.label
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.DietStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A beépített receptbank akkor ér valamit, ha a felhasználó kizárásai mellett is marad
 * belőle étel.
 *
 * MIÉRT ÉLES HIBA EZ. A tartalék tervező akkor lép működésbe, amikor a rendes tervezés
 * elakad. Ha ilyenkor egyetlen sablon sem adható ki, az `OfflineMealAi` hibát dob — a
 * felhasználó tehát pont a legrosszabb pillanatban kap egy üres képernyőt. A régi bank
 * (3 reggeli, 5 főétel, 3 nassolnivaló) mindhárom reggelijére tett tejterméket, vagyis
 * egy vegán felhasználónak BIZTOSAN nem volt reggelije.
 *
 * MIÉRT NEM ELÉG SZEMRE MEGNÉZNI. A [RestrictionChecker] szóvégi egyezést is használ,
 * és a magyar összetett szavak miatt ez meglepő: a „rizstészta" gluténnek számít (a
 * „tészta" miatt), a „kókusztej" viszont nem tejterméknek (kivétel), a „rolled oats"
 * pedig angolul gluténnek (a „roll" miatt). Ezt csak futtatva lehet tudni.
 *
 * MINDKÉT NYELVEN mérünk: az angol és a magyar kulcsszólista külön adat, és egy sablon
 * simán lehet az egyik nyelven biztonságos, a másikon nem.
 */
class RecipeBankCoverageTest {

    private val slots = listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.AFTERNOON_SNACK)

    private fun safeCount(
        slot: MealSlot,
        restrictions: Set<DietRestriction>,
        language: AppLanguage,
    ): Int = RecipeBank.forSlot(slot).count { template ->
        RestrictionChecker.isSafe(
            template.name.get(language),
            template.ingredients.map { it.name.get(language) },
            restrictions,
            language,
        )
    }

    /**
     * Minden slotra legyen legalább [minimum] kiadható sablon, mindkét nyelven.
     *
     * A hetes alsó korlát nem önkényes: harminc napos tervnél ennyi alatt ugyanaz a
     * fogás négynél többször térne vissza ugyanabban a slotban. A mostani bank ennél
     * bőségesebb — a legszűkebb eset a gluténmentes + tejmentes + tojásmentes +
     * diómentes profil, ott is kilenc nassolnivaló marad —, tehát a teszt a ROMLÁST
     * fogja meg, nem a jelent szorítja.
     */
    private fun assertCovered(
        what: String,
        restrictions: Set<DietRestriction>,
        minimum: Int = 7,
    ) {
        for (language in AppLanguage.entries) {
            for (slot in slots) {
                val count = safeCount(slot, restrictions, language)
                assertTrue(
                    "$what ($language, $slot): csak $count sablon adható ki, " +
                        "legalább $minimum kell. A bankot bővíteni kell, nem a tesztet lazítani.",
                    count >= minimum,
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Étrendi stílusok
    // -------------------------------------------------------------------------

    @Test
    fun `every diet style has meals in every slot`() {
        for (style in DietStyle.entries) {
            assertCovered("Étrendi stílus: ${style.hu}", DietRestriction.impliedBy(style))
        }
    }

    @Test
    fun `a vegan gluten-free user still gets a full day`() {
        // A legszűkebb GYAKORI kombináció. Ez volt az eredeti hiba: vegánként egyetlen
        // reggeli sem maradt, gluténmentesen pedig a főételek fele kiesett.
        assertCovered(
            "Vegán + gluténmentes",
            DietRestriction.impliedBy(DietStyle.VEGAN) + DietRestriction.GLUTEN,
        )
    }

    @Test
    fun `a vegan with a nut and soy allergy still gets a full day`() {
        assertCovered(
            "Vegán + diófélék + földimogyoró + szója",
            DietRestriction.impliedBy(DietStyle.VEGAN) +
                setOf(DietRestriction.TREE_NUT, DietRestriction.PEANUT, DietRestriction.SOY),
        )
    }

    // -------------------------------------------------------------------------
    // Egyedi kizárások
    // -------------------------------------------------------------------------

    @Test
    fun `every single restriction leaves meals in every slot`() {
        for (restriction in DietRestriction.entries) {
            assertCovered("Kizárás: ${restriction.hu}", setOf(restriction))
        }
    }

    @Test
    fun `the four most common allergies leave meals in every slot`() {
        // Az EU 14 allergénje közül ez a négy a leggyakoribb együttállás.
        assertCovered(
            "Glutén + tej + tojás + diófélék",
            setOf(
                DietRestriction.GLUTEN,
                DietRestriction.MILK_PROTEIN,
                DietRestriction.LACTOSE,
                DietRestriction.EGG,
                DietRestriction.TREE_NUT,
                DietRestriction.PEANUT,
            ),
        )
    }

    @Test
    fun `halal and kosher users get meals in every slot`() {
        assertCovered("Halal", setOf(DietRestriction.HALAL), minimum = 15)
        assertCovered("Kóser", setOf(DietRestriction.KOSHER), minimum = 15)
    }

    // -------------------------------------------------------------------------
    // A bank belső épsége
    // -------------------------------------------------------------------------

    @Test
    fun `the bank is big enough to fill a month without repeating too soon`() {
        // A tervező a nap és a fogás sorszámából választ, tehát a bank mérete szabja
        // meg, hány nap múlva ismétel. Harminc napos tervnél a három slot ennyit kér.
        assertTrue("Reggeli: ${RecipeBank.forSlot(MealSlot.BREAKFAST).size}", RecipeBank.forSlot(MealSlot.BREAKFAST).size >= 20)
        assertTrue("Főétel: ${RecipeBank.forSlot(MealSlot.LUNCH).size}", RecipeBank.forSlot(MealSlot.LUNCH).size >= 30)
        assertTrue("Nassolnivaló: ${RecipeBank.forSlot(MealSlot.AFTERNOON_SNACK).size}", RecipeBank.forSlot(MealSlot.AFTERNOON_SNACK).size >= 15)
    }

    @Test
    fun `every template is complete in both languages`() {
        for (template in RecipeBank.ALL) {
            val hu = template.name.get(AppLanguage.HU)
            val en = template.name.get(AppLanguage.EN)
            assertTrue("Üres magyar név", hu.isNotBlank())
            assertTrue("Üres angol név: $hu", en.isNotBlank())
            assertTrue("A leírás hiányzik: $hu", template.description.get(AppLanguage.HU).isNotBlank())
            assertTrue("Az angol leírás hiányzik: $hu", template.description.get(AppLanguage.EN).isNotBlank())
            assertTrue("Recept nélküli sablon: $hu", template.steps.isNotEmpty())
            assertTrue("Hozzávaló nélküli sablon: $hu", template.ingredients.size >= 2)
            assertTrue("Elkészítési idő: $hu", template.prepMinutes in 1..180)

            for (step in template.steps) {
                assertTrue("Üres lépés: $hu", step.get(AppLanguage.HU).isNotBlank())
                assertTrue("Üres angol lépés: $hu", step.get(AppLanguage.EN).isNotBlank())
            }
            for (ingredient in template.ingredients) {
                assertTrue("Üres hozzávalónév: $hu", ingredient.name.get(AppLanguage.HU).isNotBlank())
                assertTrue("Üres angol hozzávalónév: $hu", ingredient.name.get(AppLanguage.EN).isNotBlank())
                assertTrue("Nulla mennyiség (${ingredient.name.get(AppLanguage.HU)}): $hu", ingredient.quantity > 0)
                assertTrue("Ismeretlen polc (${ingredient.aisle}): $hu", ingredient.aisle in AISLES)
            }
        }
    }

    @Test
    fun `no template name appears twice`() {
        for (slot in slots) {
            val names = RecipeBank.forSlot(slot).map { it.name.get(AppLanguage.HU) }
            assertEquals("Ismétlődő fogásnév a(z) $slot bankban", names.size, names.toSet().size)
        }
    }

    @Test
    fun `the stated nutrition adds up to the stated calories`() {
        // A kalóriát a tervező a makrókkal EGYÜTT skálázza, tehát ha a kettő nem áll
        // összhangban a sablonban, az arány a napi összesítőben is hamis marad.
        for (template in RecipeBank.ALL) {
            val n = template.nutrition
            val fromMacros = n.proteinG * 4 + n.carbsG * 4 + n.fatG * 9
            assertTrue(
                "${template.name.get(AppLanguage.HU)}: a makrókból ${fromMacros.toInt()} kcal jön ki, " +
                    "a sablon ${n.kcal.toInt()} kcal-t állít",
                kotlin.math.abs(fromMacros - n.kcal) <= n.kcal * 0.12,
            )
            assertTrue("${template.name.get(AppLanguage.HU)}: nincs fehérje", n.proteinG > 0)
            assertTrue("${template.name.get(AppLanguage.HU)}: a cukor több a szénhidrátnál", n.sugarG <= n.carbsG)
        }
    }

    @Test
    fun `scaling keeps the meal within reach of the target`() {
        val template: RecipeTemplate = RecipeBank.forSlot(MealSlot.LUNCH).first()
        val meal = template.scaledTo(700.0, MealSlot.LUNCH, "12:30", AppLanguage.HU)
        assertTrue("A skálázott kalória: ${meal.nutrition.kcal}", meal.nutrition.kcal in 690.0..710.0)
        assertTrue("A hozzávalók is skálázódnak", meal.ingredients.any { !it.pantryStaple })
        // A kamrai alap (olaj, só) mennyisége NEM változhat.
        val staple = template.ingredients.first { it.pantryStaple }
        val scaledStaple = meal.ingredients.first { it.name == staple.name.get(AppLanguage.HU) }
        assertEquals(staple.quantity, scaledStaple.quantity, 0.001)
    }

    /**
     * Az `OfflineMealAi.safeMeal` választási logikája, ugyanazzal a képlettel.
     *
     * A tervező maga az :app modulban él (Room, Compose, Android SDK), tehát itt nem
     * futtatható — a KIVÁLASZTÁS viszont tiszta függvény, és a bank is itt van. A
     * lemásolt három sor azt éri el, hogy a bank bővítése ne csak elvben, hanem a
     * tényleges forgatásban is mérhető legyen.
     */
    private fun pick(
        slot: MealSlot,
        startIndex: Int,
        restrictions: Set<DietRestriction>,
        language: AppLanguage,
    ): String? {
        val bank = RecipeBank.forSlot(slot)
        for (offset in bank.indices) {
            val candidate = bank[(startIndex + offset) % bank.size]
            if (RestrictionChecker.isSafe(
                    candidate.name.get(language),
                    candidate.ingredients.map { it.name.get(language) },
                    restrictions,
                    language,
                )
            ) {
                return candidate.name.get(language)
            }
        }
        return null
    }

    @Test
    fun `a thirty day plan never runs out and does not chew the same four dishes`() {
        // Napi négy étkezés az alapértelmezés: reggeli, ebéd, uzsonna, vacsora.
        val slots = listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.AFTERNOON_SNACK, MealSlot.DINNER)
        val cases = mapOf(
            "kizárás nélkül" to emptySet(),
            "vegán" to DietRestriction.impliedBy(DietStyle.VEGAN),
            "vegán + gluténmentes" to DietRestriction.impliedBy(DietStyle.VEGAN) + DietRestriction.GLUTEN,
            "glutén + tej + tojás + dió" to setOf(
                DietRestriction.GLUTEN, DietRestriction.MILK_PROTEIN, DietRestriction.LACTOSE,
                DietRestriction.EGG, DietRestriction.TREE_NUT, DietRestriction.PEANUT,
            ),
            "FODMAP" to setOf(DietRestriction.FODMAP),
        )

        for (language in AppLanguage.entries) {
            for ((label, restrictions) in cases) {
                val chosen = mutableListOf<String>()
                for (day in 0 until 30) {
                    slots.forEachIndexed { slotIndex, slot ->
                        val name = pick(slot, day * slots.size + slotIndex, restrictions, language)
                        assertTrue(
                            "$label ($language), $day. nap / $slot: nincs kiadható fogás — " +
                                "a tervező itt HIBÁT dobna, és a felhasználó üres tervet kapna.",
                            name != null,
                        )
                        chosen += name!!
                    }
                }
                // Harminc nap = 120 fogás. Húsz különböző alatt a terv már unalmas.
                val distinct = chosen.toSet().size
                assertTrue(
                    "$label ($language): harminc nap alatt csak $distinct különböző fogás jön ki.",
                    distinct >= 20,
                )
            }
        }
    }

    @Test
    fun `the restriction labels used in this test all exist`() {
        // Ha valaki átnevez egy kizárást, ez a teszt essen el, ne csendben menjen át.
        assertTrue(DietRestriction.GLUTEN.label(AppLanguage.HU).isNotBlank())
        assertTrue(DietRestriction.entries.size >= 20)
    }

    private companion object {
        val AISLES = hu.mealpilot.core.ai.Aisle.entries.map { it.name }.toSet()
    }
}
