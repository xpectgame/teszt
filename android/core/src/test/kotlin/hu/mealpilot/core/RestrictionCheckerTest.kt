package hu.mealpilot.core

import hu.mealpilot.core.ai.AiDay
import hu.mealpilot.core.ai.AiIngredient
import hu.mealpilot.core.ai.AiMeal
import hu.mealpilot.core.ai.AiPlanResponse
import hu.mealpilot.core.ai.RestrictionChecker
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.model.DietRestriction
import hu.mealpilot.core.model.DietStyle
import hu.mealpilot.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictionCheckerTest {

    private fun hits(name: String, vararg restrictions: DietRestriction) =
        RestrictionChecker.violations(name, restrictions.toSet()).map { it.restriction }

    /** Angol hozzávalónév ellenőrzése — az angol terveké ugyanaz a felelősség. */
    private fun en(name: String, vararg restrictions: DietRestriction) =
        RestrictionChecker.violations(name, restrictions.toSet(), AppLanguage.EN).map { it.restriction }

    @Test
    fun `gluten is caught in hungarian compound words`() {
        assertEquals(listOf(DietRestriction.GLUTEN), hits("búzaliszt", DietRestriction.GLUTEN))
        assertEquals(listOf(DietRestriction.GLUTEN), hits("teljes kiőrlésű kenyér", DietRestriction.GLUTEN))
        assertEquals(listOf(DietRestriction.GLUTEN), hits("durum tészta", DietRestriction.GLUTEN))
    }

    @Test
    fun `free-from products are not flagged`() {
        assertTrue(hits("gluténmentes tészta", DietRestriction.GLUTEN).isEmpty())
        assertTrue(hits("laktózmentes tej", DietRestriction.LACTOSE).isEmpty())
        assertTrue(hits("növényi tejszín", DietRestriction.LACTOSE).isEmpty())
    }

    @Test
    fun `casein is stricter than lactose - lactose-free milk still counts`() {
        assertTrue(hits("laktózmentes tej", DietRestriction.LACTOSE).isEmpty())
        assertEquals(
            listOf(DietRestriction.MILK_PROTEIN),
            hits("laktózmentes tej", DietRestriction.MILK_PROTEIN),
        )
        assertEquals(
            listOf(DietRestriction.MILK_PROTEIN),
            hits("laktózmentes tejföl", DietRestriction.MILK_PROTEIN),
        )
    }

    @Test
    fun `dairy words are caught by their stem`() {
        listOf("tejföl", "tejszín", "trappista sajt", "görög joghurt", "vaj", "túró").forEach {
            assertEquals("$it legyen találat", 1, hits(it, DietRestriction.LACTOSE).size)
        }
    }

    @Test
    fun `peanut butter is flagged for peanut allergy`() {
        assertEquals(listOf(DietRestriction.PEANUT), hits("mogyoróvaj", DietRestriction.PEANUT))
        assertEquals(listOf(DietRestriction.PEANUT), hits("földimogyoró", DietRestriction.PEANUT))
    }

    @Test
    fun `fish names are caught but unrelated ingredients are not`() {
        assertEquals(listOf(DietRestriction.FISH), hits("lazacfilé", DietRestriction.FISH))
        assertEquals(listOf(DietRestriction.FISH), hits("tonhal konzerv", DietRestriction.FISH))
        assertTrue(hits("saláta", DietRestriction.FISH).isEmpty())
        assertTrue(hits("hagyma", DietRestriction.FISH).isEmpty())
    }

    @Test
    fun `word-stem matching does not misfire on lookalike ingredients`() {
        // A „bor" kulcsszó régen eltalálta volna a borsót, a borsot és a borjút is.
        assertTrue(hits("borsó", DietRestriction.NO_ALCOHOL).isEmpty())
        assertTrue(hits("őrölt bors", DietRestriction.NO_ALCOHOL).isEmpty())
        assertTrue(hits("borjúhús", DietRestriction.NO_ALCOHOL).isEmpty())
        // ...a babérlevél pedig a babot.
        assertTrue(hits("babérlevél", DietRestriction.FODMAP).isEmpty())
        assertEquals(listOf(DietRestriction.FODMAP), hits("fehér bab", DietRestriction.FODMAP))
        // ...viszont a valódi bor igen.
        assertEquals(listOf(DietRestriction.NO_ALCOHOL), hits("száraz vörösbor", DietRestriction.NO_ALCOHOL))
    }

    @Test
    fun `pork words are caught for the no-pork choice`() {
        assertEquals(listOf(DietRestriction.NO_PORK), hits("sertéskaraj", DietRestriction.NO_PORK))
        assertEquals(listOf(DietRestriction.NO_PORK), hits("füstölt szalonna", DietRestriction.NO_PORK))
        assertTrue(hits("csirkemell", DietRestriction.NO_PORK).isEmpty())
    }

    @Test
    fun `an ingredient can violate several restrictions at once`() {
        val result = RestrictionChecker.violations(
            "sajtos-tejfölös csirke",
            setOf(DietRestriction.LACTOSE, DietRestriction.NO_POULTRY, DietRestriction.GLUTEN),
        )
        assertEquals(
            setOf(DietRestriction.LACTOSE, DietRestriction.NO_POULTRY),
            result.map { it.restriction }.toSet(),
        )
    }

    @Test
    fun `no restrictions means no problems`() {
        assertTrue(RestrictionChecker.violations("búzaliszt", emptySet()).isEmpty())
        assertTrue(RestrictionChecker.check(plan("búzaliszt"), emptySet()).isEmpty())
    }

    @Test
    fun `plan check reports the day, the meal and the rule`() {
        val problems = RestrictionChecker.check(plan("búzaliszt"), setOf(DietRestriction.GLUTEN))
        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("0. nap"))
        assertTrue(problems[0].contains("Reggeli tál"))
        assertTrue(problems[0].contains("búzaliszt"))
        assertTrue(problems[0].contains("Glutén"))
    }

    @Test
    fun `a suspicious meal name is caught even when the ingredients look clean`() {
        val sneaky = AiPlanResponse(
            days = listOf(
                AiDay(
                    dayIndex = 2,
                    meals = listOf(
                        AiMeal(
                            name = "Rántott sajt rizzsel",
                            ingredients = listOf(AiIngredient(name = "rizs", quantity = 80.0, unit = "g")),
                        )
                    ),
                )
            )
        )
        val problems = RestrictionChecker.check(sneaky, setOf(DietRestriction.LACTOSE))
        assertTrue(problems.any { it.contains("fogásnév") })
    }

    @Test
    fun `vegan style implies dairy, egg and meat exclusions without ticking them`() {
        val vegan = UserProfile(dietStyle = DietStyle.VEGAN)
        assertTrue(DietRestriction.MILK_PROTEIN in vegan.effectiveRestrictions)
        assertTrue(DietRestriction.EGG in vegan.effectiveRestrictions)
        assertTrue(DietRestriction.NO_POULTRY in vegan.effectiveRestrictions)
        assertFalse(DietRestriction.GLUTEN in vegan.effectiveRestrictions)

        val problems = RestrictionChecker.check(plan("görög joghurt"), vegan.effectiveRestrictions)
        assertTrue(problems.isNotEmpty())
    }

    @Test
    fun `vegetarian keeps dairy but drops meat and fish`() {
        val vegetarian = UserProfile(dietStyle = DietStyle.VEGETARIAN)
        assertFalse(DietRestriction.MILK_PROTEIN in vegetarian.effectiveRestrictions)
        assertTrue(DietRestriction.FISH in vegetarian.effectiveRestrictions)
        assertTrue(RestrictionChecker.check(plan("görög joghurt"), vegetarian.effectiveRestrictions).isEmpty())
        assertTrue(RestrictionChecker.check(plan("lazacfilé"), vegetarian.effectiveRestrictions).isNotEmpty())
    }

    @Test
    fun `ticked restrictions add to the ones implied by the style`() {
        val profile = UserProfile(
            dietStyle = DietStyle.VEGETARIAN,
            restrictions = setOf(DietRestriction.GLUTEN, DietRestriction.TREE_NUT),
        )
        assertTrue(DietRestriction.GLUTEN in profile.effectiveRestrictions)
        assertTrue(DietRestriction.FISH in profile.effectiveRestrictions)
        assertEquals(
            DietRestriction.impliedBy(DietStyle.VEGETARIAN).size + 2,
            profile.effectiveRestrictions.size,
        )
    }

    @Test
    fun `every restriction has keywords and a rule`() {
        DietRestriction.entries.forEach {
            assertTrue("${it.name}: hiányzik a kulcsszó", it.keywords.isNotEmpty())
            assertTrue("${it.name}: hiányzik a szabály", it.rule.isNotBlank())
            assertTrue("${it.name}: hiányzik a magyar név", it.hu.isNotBlank())
        }
        assertEquals(DietRestriction.entries.size, DietRestriction.byGroup().values.sumOf { it.size })
    }

    private fun plan(ingredient: String) = AiPlanResponse(
        days = listOf(
            AiDay(
                dayIndex = 0,
                meals = listOf(
                    AiMeal(
                        name = "Reggeli tál",
                        ingredients = listOf(AiIngredient(name = ingredient, quantity = 100.0, unit = "g")),
                    )
                ),
            )
        )
    )

    // ---------- Angol ----------
    //
    // A magyar összetett szó elöl hordozza a lényeget („búzaliszt"), az angol hátul
    // („wholewheat"). Ez két külön illesztési szabály, tehát két külön tesztsor is:
    // a magyar zöldje semmit nem mond az angolról.

    @Test
    fun `gluten is caught in english compound words`() {
        for (name in listOf("wholewheat bread", "breadcrumbs", "durum pasta", "rye flour", "barley malt")) {
            assertEquals("$name should be flagged", listOf(DietRestriction.GLUTEN), en(name, DietRestriction.GLUTEN))
        }
    }

    @Test
    fun `english free-from products are not flagged`() {
        for (name in listOf("gluten-free pasta", "gluten free bread", "buckwheat flour", "rice flour")) {
            assertTrue("$name should be safe", en(name, DietRestriction.GLUTEN).isEmpty())
        }
    }

    @Test
    fun `soy is caught in every common english spelling`() {
        for (name in listOf("soy sauce", "soya milk", "soybean oil", "tofu", "tempeh", "edamame beans", "tamari")) {
            assertEquals("$name should be flagged", listOf(DietRestriction.SOY), en(name, DietRestriction.SOY))
        }
    }

    @Test
    fun `short english keywords do not swallow unrelated words`() {
        // Ezek a valódi csapdák: a rövid kulcsszó beleolvad egy másik szóba. Egy téves
        // találat itt nem kényelmetlenség — kiveszi az étrendből az ártatlan alapanyagot,
        // és a felhasználó megtanulja, hogy a szűrőnek nem kell hinni.
        assertTrue(en("eggplant", DietRestriction.EGG).isEmpty())
        assertTrue(en("chamomile tea", DietRestriction.NO_PORK).isEmpty())
        assertTrue(en("nutmeg", DietRestriction.TREE_NUT).isEmpty())
        assertTrue(en("coconut milk", DietRestriction.TREE_NUT).isEmpty())
        // …de a valódi találatot nem szabad elengedni:
        assertEquals(listOf(DietRestriction.EGG), en("eggs", DietRestriction.EGG))
        assertEquals(listOf(DietRestriction.EGG), en("egg white", DietRestriction.EGG))
    }

    @Test
    fun `english dairy is caught where the head word is at the end`() {
        for (name in listOf("buttermilk", "whole milk", "greek yoghurt", "cheddar cheese", "whey protein")) {
            assertTrue("$name should be flagged", en(name, DietRestriction.LACTOSE).isNotEmpty())
        }
        assertTrue(en("oat milk", DietRestriction.LACTOSE).isEmpty())
        assertTrue(en("lactose-free milk", DietRestriction.LACTOSE).isEmpty())
    }

    // ---------------------------------------------------------------------------
    // Magyar szóalaktan. Mindhárom eset ÉLES HIBA volt: az allergén átcsúszott a
    // szűrőn, és a felhasználóhoz jutott volna.
    // ---------------------------------------------------------------------------

    @Test
    fun `stem lengthening does not hide an allergen`() {
        // A magyar tővégi magánhangzó nyúlik toldalékoláskor: tészta -> tésztával.
        // A „tészta" végig benne volt a gluténlistában, a „húsleves tésztával" mégis
        // átment, mert nem a kulcsszó karaktersorával kezdődik.
        assertEquals(listOf(DietRestriction.GLUTEN), hits("húsleves tésztával", DietRestriction.GLUTEN))
        assertEquals(listOf(DietRestriction.GLUTEN), hits("palacsintával", DietRestriction.GLUTEN))
        assertEquals(listOf(DietRestriction.GLUTEN), hits("zsemlét", DietRestriction.GLUTEN))
        assertEquals(listOf(DietRestriction.FODMAP), hits("hagymával", DietRestriction.FODMAP))
        assertEquals(listOf(DietRestriction.NO_POULTRY), hits("csirkével", DietRestriction.NO_POULTRY))
    }

    @Test
    fun `an allergen at the end of a compound is caught`() {
        // A magyar mindkét irányban összetesz. A „tejföl" elöl hordozza az allergént,
        // a „krémsajt" és a „juhtúró" hátul — utóbbiakat a csak szóeleji egyezés
        // némán átengedte.
        assertEquals(listOf(DietRestriction.LACTOSE), hits("krémsajt", DietRestriction.LACTOSE))
        assertEquals(listOf(DietRestriction.LACTOSE), hits("juhtúró", DietRestriction.LACTOSE))
        assertEquals(listOf(DietRestriction.NO_PORK), hits("zsírszalonna", DietRestriction.NO_PORK))
    }

    @Test
    fun `breaded dishes are gluten, scrambled eggs are not`() {
        // A „rántott" (panírozott) és a „rántotta" (tojásétel) ugyanazzal a hét
        // betűvel kezdődik. Ha a gluténkulcsszó szóköz nélkül állna, minden rántottát
        // gluténesnek jelölnénk — az pedig minden reggelis tervet javító körbe küldene.
        assertEquals(listOf(DietRestriction.GLUTEN), hits("rántott csirkemell", DietRestriction.GLUTEN))
        assertEquals(emptyList<DietRestriction>(), hits("rántotta", DietRestriction.GLUTEN))
        assertEquals(emptyList<DietRestriction>(), hits("rántotta paradicsommal", DietRestriction.GLUTEN))
        // A rántotta viszont tojás, és azt meg kell fognia.
        assertEquals(listOf(DietRestriction.EGG), hits("rántotta", DietRestriction.EGG))
    }

    @Test
    fun `common hungarian dishes are matched to the right allergen`() {
        assertEquals(listOf(DietRestriction.GLUTEN), hits("túrós csusza", DietRestriction.GLUTEN))
        assertEquals(listOf(DietRestriction.GLUTEN), hits("pirítós", DietRestriction.GLUTEN))
        assertEquals(listOf(DietRestriction.GLUTEN), hits("spagetti", DietRestriction.GLUTEN))
        assertEquals(listOf(DietRestriction.LACTOSE), hits("trappista", DietRestriction.LACTOSE))
        assertEquals(listOf(DietRestriction.EGG), hits("tiramisu", DietRestriction.EGG))
        assertEquals(listOf(DietRestriction.EGG), hits("habcsók", DietRestriction.EGG))
        assertEquals(listOf(DietRestriction.EGG), hits("aioli", DietRestriction.EGG))
        assertEquals(listOf(DietRestriction.NO_PORK), hits("tepertő", DietRestriction.NO_PORK))
        assertEquals(listOf(DietRestriction.NO_PORK), hits("disznósajt", DietRestriction.NO_PORK))
    }

    @Test
    fun `the widened rule does not start flagging innocent food`() {
        // A lazítás ára a fals riasztás lenne: minden téves találat fölösleges javító
        // kört jelent, ami időbe és pénzbe kerül. Ezek maradjanak tiszták.
        for (name in listOf("rizs", "burgonyapüré", "kukoricakása", "paradicsom", "padlizsán", "banán")) {
            assertEquals(
                "$name nem ütközhet semmivel",
                emptyList<DietRestriction>(),
                hits(name, DietRestriction.GLUTEN, DietRestriction.LACTOSE, DietRestriction.EGG),
            )
        }
        // A mentes termékek jelzése továbbra is erősebb a kulcsszónál.
        assertEquals(emptyList<DietRestriction>(), hits("gluténmentes tészta", DietRestriction.GLUTEN))
        assertEquals(emptyList<DietRestriction>(), hits("laktózmentes sajt", DietRestriction.LACTOSE))
        assertEquals(emptyList<DietRestriction>(), hits("mandulatej", DietRestriction.LACTOSE))
        // A mogyoróhagyma (salotta) egyik diófélével sem ütközik.
        assertEquals(emptyList<DietRestriction>(), hits("mogyoróhagyma", DietRestriction.PEANUT))
        assertEquals(emptyList<DietRestriction>(), hits("mogyoróhagyma", DietRestriction.TREE_NUT))
    }

    @Test
    fun `a space inside a keyword carries meaning and must survive`() {
        // A kulcsszavak korábban trimmelve mentek az összehasonlításba, és ezzel két
        // szándék némán elveszett. Az egyik a „rántott " (fent). A másik a „gm "
        // gluténmentes-jelölő: szóköz nélkül a rövidítés beleolvadna más szavakba, és
        // egy TÉVES biztonsági jelölő elrejtené az allergént — ez a veszélyesebb irány.
        assertEquals(emptyList<DietRestriction>(), hits("gm tészta", DietRestriction.GLUTEN))
        // A jelölő nélkül viszont ugyanaz a hozzávaló ütközik.
        assertEquals(listOf(DietRestriction.GLUTEN), hits("tészta", DietRestriction.GLUTEN))
    }

    @Test
    fun `a meal can be judged safe or unsafe before it is ever served`() {
        // A beépített, sablonos tervező maga rakja ki a fogásokat, és nincs kit
        // megkérni a javításra: amit kiad, az egyenesen a felhasználóhoz kerül.
        // Ezért kell tudni ELŐRE, hogy egy fogás kiadható-e.
        val walnutYoghurt = AiMeal(
            name = "Görög joghurt dióval",
            ingredients = listOf(
                AiIngredient(name = "görög joghurt", quantity = 150.0, unit = "g"),
                AiIngredient(name = "dió", quantity = 20.0, unit = "g"),
            ),
        )
        val oatmeal = AiMeal(
            name = "Zabkása vízzel",
            ingredients = listOf(AiIngredient(name = "zabpehely", quantity = 60.0, unit = "g")),
        )

        assertFalse(
            "Diós fogás nem adható mogyoróallergiásnak",
            RestrictionChecker.isSafe(walnutYoghurt, setOf(DietRestriction.TREE_NUT), AppLanguage.HU),
        )
        assertFalse(
            "Joghurtos fogás nem adható laktózérzékenynek",
            RestrictionChecker.isSafe(walnutYoghurt, setOf(DietRestriction.LACTOSE), AppLanguage.HU),
        )
        assertTrue(
            "Ami nem ütközik, az kiadható",
            RestrictionChecker.isSafe(oatmeal, setOf(DietRestriction.TREE_NUT, DietRestriction.LACTOSE), AppLanguage.HU),
        )
        assertTrue(
            "Kizárás nélkül minden kiadható",
            RestrictionChecker.isSafe(walnutYoghurt, emptySet(), AppLanguage.HU),
        )
    }

    @Test
    fun `a dangerous name is caught even when the ingredients look clean`() {
        // A sablon hozzávalólistája hiányos lehet; a fogás NEVE is árulkodik.
        val meal = AiMeal(
            name = "Mogyoróvajas pirítós",
            ingredients = listOf(AiIngredient(name = "kenyér", quantity = 60.0, unit = "g")),
        )
        assertFalse(
            RestrictionChecker.isSafe(meal, setOf(DietRestriction.PEANUT), AppLanguage.HU),
        )
    }

    @Test
    fun `the hungarian word for hazelnut also means peanut in everyday use`() {
        // Ez egy KORÁBBI, szándékos döntés megfordítása. A régi szabály botanikailag
        // helyes volt: a mogyoró dióféle (Corylus), a földimogyoró hüvelyes (Arachis),
        // és a kettő külön allergia.
        //
        // A magyar boltban viszont a „sós mogyoró" és a „pörkölt mogyoró" földimogyoró.
        // A régi szabály mellett az a földimogyoró-allergiás, aki a dióféléket NEM
        // jelölte be — és nincs is rá oka, ha csak a földimogyoróra allergiás —,
        // pontosan a legveszélyesebb allergént kapta volna meg némán.
        //
        // A két hiba ára nem egyforma: a téves találat egy javító kör, a kimaradté egy
        // anafilaxia. Az app máshol is ezt az irányt választja („inkább maradjon a régi
        // nap, mint hogy allergén kerüljön a tervbe").
        assertEquals(listOf(DietRestriction.PEANUT), hits("sós mogyoró", DietRestriction.PEANUT))
        assertEquals(listOf(DietRestriction.PEANUT), hits("pörkölt mogyoró", DietRestriction.PEANUT))

        // Az ára: a valódi dióféle is földimogyoró-találatot ad. Ezt vállaljuk.
        assertEquals(listOf(DietRestriction.PEANUT), hits("mogyorókrém", DietRestriction.PEANUT))
    }

    @Test
    fun `everyday hungarian dishes are not mistaken for shellfish or molluscs`() {
        // A „rak" (ékezet nélküli rák) a magyar egyik legtermékenyebb szótöve. Emiatt
        // MINDEN rakott étel rákallergiás találat volt — a rakott krumpli, a rakott kel
        // és a rakéta saláta is. Nem elméleti: ezek a leggyakoribb magyar fogások közé
        // tartoznak, és minden találat egy fölösleges, fizetős javító kör.
        for (name in listOf("rakott krumpli", "rakott kel", "rakott tészta", "rakéta saláta")) {
            assertEquals(name, emptyList<DietRestriction>(), hits(name, DietRestriction.CRUSTACEAN))
        }
        assertEquals(emptyList<DietRestriction>(), hits("rákóczi túrós", DietRestriction.CRUSTACEAN))
        // A valódi rák továbbra is találat.
        assertEquals(listOf(DietRestriction.CRUSTACEAN), hits("folyami rák", DietRestriction.CRUSTACEAN))
        assertEquals(listOf(DietRestriction.CRUSTACEAN), hits("garnélarák", DietRestriction.CRUSTACEAN))

        // A csigatészta a húsleves tartozéka, nem csiga.
        assertEquals(emptyList<DietRestriction>(), hits("csigatészta", DietRestriction.MOLLUSC))
        assertEquals(emptyList<DietRestriction>(), hits("húsleves csigatésztával", DietRestriction.MOLLUSC))
        assertEquals(listOf(DietRestriction.MOLLUSC), hits("fekete kagyló", DietRestriction.MOLLUSC))
    }

    @Test
    fun `the fish prefix does not swallow cheese and celery`() {
        // A „hal" előtag beleragadt a halloumiba (sajt) és a halványító zellerbe.
        assertEquals(emptyList<DietRestriction>(), hits("halloumi", DietRestriction.FISH))
        assertEquals(emptyList<DietRestriction>(), hits("halványító zeller", DietRestriction.FISH))
        // A valódi hal továbbra is találat.
        assertEquals(listOf(DietRestriction.FISH), hits("füstölt lazac", DietRestriction.FISH))
        assertEquals(listOf(DietRestriction.FISH), hits("halfilé", DietRestriction.FISH))
    }

    @Test
    fun `vegetables are not mistaken for dairy or beans`() {
        // A „vaj" előtag beleragadt a vajbabba és a vajretekbe, a „bab" a
        // babapiskótába. Mind a három hétköznapi magyar alapanyag.
        assertEquals(emptyList<DietRestriction>(), hits("vajbabfőzelék", DietRestriction.LACTOSE))
        assertEquals(emptyList<DietRestriction>(), hits("vajbab", DietRestriction.MILK_PROTEIN))
        assertEquals(emptyList<DietRestriction>(), hits("vajretek", DietRestriction.LACTOSE))
        assertEquals(emptyList<DietRestriction>(), hits("babapiskóta", DietRestriction.FODMAP))
        // A valódi vaj és bab továbbra is találat.
        assertEquals(listOf(DietRestriction.LACTOSE), hits("vajas pirítós", DietRestriction.LACTOSE))
        assertEquals(listOf(DietRestriction.FODMAP), hits("fehér bab", DietRestriction.FODMAP))
    }

    @Test
    fun `an exception survives hungarian suffixes`() {
        // A magyar toldalék megnyújtja a tővéghangzót: „csigatészta" -> „csigatésztával".
        // A kivételeket ezért TŐALAKBAN kell megadni, különben a toldalékos alakon
        // átcsúszik a találat — és pont a toldalékos alak fordul elő a mondatban.
        assertEquals(emptyList<DietRestriction>(), hits("csigatészta", DietRestriction.MOLLUSC))
        assertEquals(emptyList<DietRestriction>(), hits("csigatésztával", DietRestriction.MOLLUSC))
        assertEquals(emptyList<DietRestriction>(), hits("mogyoróhagymával", DietRestriction.TREE_NUT))
    }

    @Test
    fun `common bakery items missing from the hungarian list are caught`() {
        // Ezek az ANGOL kulcsszólistán rajta voltak, a magyarról lemaradtak. Egy
        // gluténérzékeny magyar felhasználó croissant-t kaphatott volna reggelire.
        for (name in listOf("vajas croissant", "bagett", "sajtos pogácsa", "briós")) {
            assertEquals(name, listOf(DietRestriction.GLUTEN), hits(name, DietRestriction.GLUTEN))
        }
    }

    @Test
    fun `sesame and soy hide behind less common names`() {
        assertEquals(listOf(DietRestriction.SESAME), hits("halva", DietRestriction.SESAME))
        assertEquals(listOf(DietRestriction.SOY), hits("tamari szósz", DietRestriction.SOY))
        // A „rák" három betű, ezért a szóvégi egyezés nem futott rá az összetételekre.
        assertEquals(listOf(DietRestriction.CRUSTACEAN), hits("tarisznyarák", DietRestriction.CRUSTACEAN))
    }

    @Test
    fun `a free-from label beats the keyword in both languages`() {
        // A mentesség jelölése eddig CSAK ott működött, ahol valaki kézzel felvette:
        // a gluténnál igen, a szójánál, a földimogyorónál és a tojásnál nem. Így a
        // „soy-free dressing" szójaütközésnek számított — fölösleges javító kör.
        assertEquals(emptyList<DietRestriction>(), en("soy-free dressing", DietRestriction.SOY))
        assertEquals(emptyList<DietRestriction>(), en("peanut-free granola", DietRestriction.PEANUT))
        assertEquals(emptyList<DietRestriction>(), en("egg-free mayo", DietRestriction.EGG))
        assertEquals(emptyList<DietRestriction>(), hits("szójamentes szósz", DietRestriction.SOY))
        assertEquals(emptyList<DietRestriction>(), hits("tejmentes margarin", DietRestriction.LACTOSE))
    }

    @Test
    fun `a free-from label only covers what it names`() {
        // Ez a szabály veszélyes határa: ha bármilyen „free" szó elnyelné a találatot,
        // a „sugar-free milk" tejmentesnek látszana. A jelölés a MEGTALÁLT kulcsszóhoz
        // van kötve, ezért ez továbbra is találat.
        assertEquals(listOf(DietRestriction.LACTOSE), en("sugar-free milk", DietRestriction.LACTOSE))
        assertEquals(listOf(DietRestriction.LACTOSE), hits("cukormentes tej", DietRestriction.LACTOSE))
        assertEquals(listOf(DietRestriction.SOY), en("gluten-free soy sauce", DietRestriction.SOY))
    }

    @Test
    fun `butter is not always dairy in english`() {
        // A mogyoróvaj a SAJÁT angol sablonjainkban is szerepel, és tejtermékként jött
        // ki. A butternut tök és a vajsaláta zöldség.
        for (name in listOf("peanut butter", "almond butter", "butternut squash", "butter lettuce", "butter beans")) {
            assertEquals(name, emptyList<DietRestriction>(), en(name, DietRestriction.LACTOSE))
            assertEquals(name, emptyList<DietRestriction>(), en(name, DietRestriction.MILK_PROTEIN))
        }
        // A valódi vaj és az író továbbra is találat.
        assertEquals(listOf(DietRestriction.LACTOSE), en("salted butter", DietRestriction.LACTOSE))
        assertEquals(listOf(DietRestriction.LACTOSE), en("buttermilk", DietRestriction.LACTOSE))
    }

    @Test
    fun `english bakery items missing from the list are caught`() {
        // A magyar listán megvoltak, az angolról hiányoztak — a két lista külön adat,
        // és külön is tud hiányos lenni.
        for (name in listOf("wholemeal toast", "sourdough loaf", "ciabatta", "focaccia")) {
            assertEquals(name, listOf(DietRestriction.GLUTEN), en(name, DietRestriction.GLUTEN))
        }
        // A hajdina nem búza — a gluténnál kivétel volt, a FODMAP-nál lemaradt.
        assertEquals(emptyList<DietRestriction>(), en("buckwheat flour", DietRestriction.FODMAP))
        assertEquals(emptyList<DietRestriction>(), en("buckwheat pancake", DietRestriction.GLUTEN))
    }

    @Test
    fun `three-letter allergen words are caught at the end of compounds too`() {
        // A szóvégi egyezés négyes küszöbe kihagyta a magyar legfontosabb RÖVID
        // allergénszavait az összetételekből. A kecsketej és a bivalytej tejtermék, a
        // teavaj vaj — egyik sem számított annak.
        assertEquals(listOf(DietRestriction.LACTOSE), hits("kecsketej", DietRestriction.LACTOSE))
        assertEquals(listOf(DietRestriction.LACTOSE), hits("bivalytej", DietRestriction.LACTOSE))
        assertEquals(listOf(DietRestriction.LACTOSE), hits("teavaj", DietRestriction.LACTOSE))
        assertEquals(listOf(DietRestriction.MILK_PROTEIN), hits("juhtej", DietRestriction.MILK_PROTEIN))
        assertEquals(listOf(DietRestriction.FRUCTOSE), hits("akácméz", DietRestriction.FRUCTOSE))
        assertEquals(listOf(DietRestriction.FISH), hits("busahal", DietRestriction.FISH))
    }

    @Test
    fun `plant milks and nut butters are not dairy`() {
        // A hármas küszöb ára: a „tej" és a „vaj" beleragad olyan szavakba is, amik
        // nem tejtermékek. Ezek pont azok az ételek, amiket egy tejallergiás KAP —
        // tehát minden téves találat nála sülne el.
        for (name in listOf("kókusztej", "zabtej", "rizstej", "mandulatej", "mogyoróvaj", "mandulavaj")) {
            assertEquals(name, emptyList<DietRestriction>(), hits(name, DietRestriction.LACTOSE))
            assertEquals(name, emptyList<DietRestriction>(), hits(name, DietRestriction.MILK_PROTEIN))
        }
        // A zöldbab alacsony FODMAP-tartalmú, a lóbab nem.
        assertEquals(emptyList<DietRestriction>(), hits("zöldbab", DietRestriction.FODMAP))
        assertEquals(listOf(DietRestriction.FODMAP), hits("lóbab", DietRestriction.FODMAP))
        // A rövid kulcsszavak régi csapdái sem élednek újra.
        assertEquals(emptyList<DietRestriction>(), hits("borsó", DietRestriction.NO_ALCOHOL))
        assertEquals(emptyList<DietRestriction>(), hits("babérlevél", DietRestriction.FODMAP))
    }

    @Test
    fun `gelatin is excluded for vegetarians and vegans`() {
        // A zselatin állati eredetű, a kocsonya és az aszpik pedig kifejezetten sertés.
        // A halal listán ott volt, a vegetáriánusén nem.
        for (style in listOf(DietStyle.VEGETARIAN, DietStyle.VEGAN)) {
            val r = UserProfile(dietStyle = style).effectiveRestrictions
            for (food in listOf("zselatin", "kocsonya", "aszpik")) {
                assertTrue(
                    "$style: $food nem mehet át",
                    RestrictionChecker.violations(food, r).isNotEmpty(),
                )
            }
        }
    }
}
