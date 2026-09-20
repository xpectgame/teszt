package hu.mealpilot.core.ai

import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.i18n.Text
import kotlin.math.roundToInt

/**
 * Beépített receptbank: ebből dolgozik a tartalék (offline) tervező.
 *
 * MIÉRT A :core MODULBAN VAN. A bank tartalom, nem felület, és tisztán Kotlin — de a
 * fontosabb ok, hogy így ELLENŐRIZHETŐ. Minden sablonnak át kell mennie a
 * [RestrictionChecker] szűrőjén ahhoz, hogy egy kizárásokkal élő felhasználó egyáltalán
 * kapjon ételt, és ez nem szemre eldönthető: a magyar szóvégi egyezés miatt a
 * „rizstészta" gluténnek számít, a „kókusztej" viszont nem tejterméknek. Amíg a bank az
 * :app modulban volt, ezt csak a CI tudta megnézni, Android SDK-t igénylő futtatással.
 *
 * MIÉRT NEM ANDROID ERŐFORRÁSBÓL JÖN. A hozzávalók neve a bevásárlólistára és a naplóba
 * is bekerül, és ott a TERV nyelvén kell állnia — nem azon, amit a telefon éppen mutat.
 * Ezért minden szöveg [Text] pár.
 *
 * MIÉRT ENNYI. A tartalék tervező akkor lép működésbe, amikor a rendes tervezés elakad
 * (hálózat, kvóta, modellhiba). Ha ilyenkor a felhasználó kizárásai miatt egyetlen
 * sablon sem adható ki, hibát dobunk — vagyis a bank szűkössége önmagában okoz
 * üzemzavart. Három reggeli, öt főétel és három nassolnivaló mellett ez nem elméleti:
 * egy vegán felhasználónak EGYETLEN reggeli sem maradt, mert mind a hármon tejtermék
 * volt. A bank ezért szándékosan sokféle konyhából merít, és minden gyakori kizáráshoz
 * hoz alternatívát; a `RecipeBankCoverageTest` mindegyiket megméri.
 */
object RecipeBank {

    /** A slothoz tartozó sablonok. Az ebéd és a vacsora ugyanabból a listából válogat. */
    fun forSlot(slot: MealSlot): List<RecipeTemplate> = when (slot) {
        MealSlot.BREAKFAST -> BREAKFASTS
        MealSlot.LUNCH, MealSlot.DINNER -> MAINS
        else -> SNACKS
    }

    /** Minden sablon, slottól függetlenül — az ellenőrzésekhez. */
    val ALL: List<RecipeTemplate> get() = BREAKFASTS + MAINS + SNACKS

    // -----------------------------------------------------------------------------
    // Rövidítések. A bank ezerszámra ismétli ugyanazt a három konstruktorhívást, és
    // a rövid alak nélkül a recept elveszne a zárójelekben.
    // -----------------------------------------------------------------------------

    private fun t(hungarian: String, english: String) = Text(hungarian, english)

    private fun ing(
        hungarian: String,
        english: String,
        qty: Double,
        unit: String,
        aisle: Aisle,
        staple: Boolean = false,
    ) = RecipeIngredient(Text(hungarian, english), qty, unit, aisle.name, staple)

    private fun kcal(
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        fiber: Double,
        sugar: Double,
    ) = AiNutrition(
        kcal = kcal, proteinG = protein, carbsG = carbs,
        fatG = fat, fiberG = fiber, sugarG = sugar,
    )

    /** Olaj, só, fűszer: kamrai alap, nem méretezzük és nem írjuk a listára külön. */
    private val OIL = ing("olívaolaj", "olive oil", 10.0, "ml", Aisle.FUSZER, staple = true)
    private val RAPESEED = ing("repceolaj", "rapeseed oil", 10.0, "ml", Aisle.FUSZER, staple = true)
    private val SALT_PEPPER = ing("só, bors", "salt and pepper", 1.0, "csipet", Aisle.FUSZER, staple = true)

    // =============================================================================
    // REGGELI
    // =============================================================================

    val BREAKFASTS: List<RecipeTemplate> = listOf(
        RecipeTemplate(
            t("Túrós-zabpelyhes tál bogyós gyümölccsel", "Quark and oat bowl with berries"),
            t("Gyors, magas fehérjetartalmú magyar reggeli.", "A quick, high-protein Hungarian breakfast."),
            5,
            listOf(
                t("Keverd simára a túrót egy villával.", "Mash the quark smooth with a fork."),
                t("Forgasd bele a zabpelyhet, és hagyd állni két percet.", "Fold in the oats and let it stand for two minutes."),
                t("Tedd a tetejére a bogyós gyümölcsöt és a diót.", "Top with the berries and the walnuts."),
            ),
            listOf(
                ing("sovány túró", "low-fat quark", 200.0, "g", Aisle.TEJTERMEK),
                ing("zabpehely", "oat flakes", 40.0, "g", Aisle.SZARAZARU),
                ing("áfonya", "blueberries", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("dió", "walnuts", 15.0, "g", Aisle.SZARAZARU),
            ),
            kcal(480.0, 38.0, 45.0, 15.0, 7.0, 12.0),
        ),
        RecipeTemplate(
            t("Rántotta teljes kiőrlésű pirítóssal", "Scrambled eggs with wholemeal toast"),
            t("Klasszikus, laktató reggeli.", "A classic, filling breakfast."),
            10,
            listOf(
                t("Verd fel a tojásokat, sózd, borsozd.", "Beat the eggs and season them."),
                t("Süsd lassú tűzön, folyamatos keverés mellett.", "Cook them slowly, stirring all the time."),
                t("Pirítsd meg a kenyeret, tedd mellé a paradicsomot.", "Toast the bread and serve the tomato alongside."),
            ),
            listOf(
                ing("tojás", "eggs", 3.0, "db", Aisle.TEJTERMEK),
                ing("teljes kiőrlésű kenyér", "wholemeal bread", 60.0, "g", Aisle.PEKARU),
                ing("paradicsom", "tomato", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(460.0, 27.0, 34.0, 22.0, 6.0, 5.0),
        ),
        RecipeTemplate(
            t("Görög joghurtos smoothie tál", "Greek yoghurt smoothie bowl"),
            t("Reggeli, ami előre elkészíthető.", "A breakfast you can make ahead."),
            5,
            listOf(
                t("Turmixold össze a joghurtot a banánnal.", "Blend the yoghurt with the banana."),
                t("Öntsd tálba, szórd meg zabbal és chia maggal.", "Pour it into a bowl and scatter over the oats and chia."),
            ),
            listOf(
                ing("görög joghurt", "Greek yoghurt", 250.0, "g", Aisle.TEJTERMEK),
                ing("banán", "banana", 1.0, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("zabpehely", "oat flakes", 30.0, "g", Aisle.SZARAZARU),
                ing("chia mag", "chia seeds", 10.0, "g", Aisle.SZARAZARU),
            ),
            kcal(450.0, 26.0, 52.0, 14.0, 8.0, 20.0),
        ),
        RecipeTemplate(
            t("Shakshuka", "Shakshuka"),
            t("Észak-afrikai serpenyős tojás fűszeres paradicsomban.", "North African eggs poached in a spiced tomato base."),
            25,
            listOf(
                t("Dinszteld meg a hagymát és a paprikát olajon.", "Soften the onion and the pepper in oil."),
                t("Add hozzá a paradicsomot, a köményt és a pirospaprikát, főzd tíz percig.", "Add the tomatoes, the cumin and the paprika, and simmer for ten minutes."),
                t("Nyomj a szószba mélyedéseket, üsd bele a tojásokat.", "Make wells in the sauce and crack the eggs into them."),
                t("Fedő alatt süsd készre, szórd meg friss petrezselyemmel.", "Cover and cook until set, then scatter over the parsley."),
            ),
            listOf(
                ing("tojás", "eggs", 3.0, "db", Aisle.TEJTERMEK),
                ing("hámozott paradicsom konzerv", "tinned chopped tomatoes", 300.0, "g", Aisle.SZARAZARU),
                ing("kaliforniai paprika", "bell pepper", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("vöröshagyma", "onion", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("őrölt kömény", "ground cumin", 2.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(430.0, 24.0, 28.0, 25.0, 7.0, 14.0),
        ),
        RecipeTemplate(
            t("Zabkása kókusztejjel és banánnal", "Oat porridge with coconut milk and banana"),
            t("Növényi, tejtermék nélküli kása — meleg reggeli hideg reggelre.", "A dairy-free porridge — a warm breakfast for a cold morning."),
            12,
            listOf(
                t("Tedd fel a zabpelyhet a kókusztejjel és egy csipet sóval.", "Put the oats on with the coconut milk and a pinch of salt."),
                t("Főzd lassú tűzön nyolc percig, kevergetve.", "Simmer for eight minutes, stirring."),
                t("Keverd bele a fahéjat, tedd a tetejére a banánt és a tökmagot.", "Stir in the cinnamon, then top with the banana and pumpkin seeds."),
            ),
            listOf(
                ing("gluténmentes zabpehely", "gluten-free oats", 70.0, "g", Aisle.SZARAZARU),
                ing("kókusztej ital", "coconut milk drink", 250.0, "ml", Aisle.ITAL),
                ing("banán", "banana", 1.0, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("tökmag", "pumpkin seeds", 20.0, "g", Aisle.SZARAZARU),
                ing("fahéj", "cinnamon", 1.0, "g", Aisle.FUSZER, staple = true),
            ),
            kcal(470.0, 14.0, 68.0, 16.0, 9.0, 18.0),
        ),
        RecipeTemplate(
            t("Hajdinakása bogyós gyümölccsel", "Buckwheat porridge with berries"),
            t("Gluténmentes, növényi kása — a hajdina nem gabona, hanem mag.", "A gluten-free, plant-based porridge — buckwheat is a seed, not a grain."),
            20,
            listOf(
                t("Öblítsd le a hajdinát, tedd fel kétszeres mennyiségű vízzel.", "Rinse the buckwheat and put it on with twice its volume of water."),
                t("Főzd tizenöt percig, amíg megszívja a vizet.", "Cook for fifteen minutes until it has drunk the water."),
                t("Keverd bele a zabtejet, a tetejére tedd a bogyós gyümölcsöt és a napraforgómagot.", "Stir in the oat milk, then top with the berries and sunflower seeds."),
            ),
            listOf(
                ing("hajdina", "buckwheat groats", 70.0, "g", Aisle.SZARAZARU),
                ing("zabtej", "oat milk", 150.0, "ml", Aisle.ITAL),
                ing("málna", "raspberries", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("napraforgómag", "sunflower seeds", 20.0, "g", Aisle.SZARAZARU),
            ),
            kcal(450.0, 14.0, 70.0, 13.0, 11.0, 12.0),
        ),
        RecipeTemplate(
            t("Besan chilla — csicseriborsós reggeli", "Besan chilla, a chickpea breakfast"),
            t("Indiai, serpenyőben sült csicseriborsós korong. Növényi és gluténmentes.", "An Indian pan-cooked chickpea round. Plant-based and gluten-free."),
            15,
            listOf(
                t("Keverd simára a gluténmentes csicseriborsólisztet vízzel, hogy sűrű tésztát kapj.", "Whisk the chickpea flour with water into a thick batter."),
                t("Fűszerezd kurkumával, köménnyel és sóval.", "Season it with turmeric, cumin and salt."),
                t("Keverd bele az apróra vágott paradicsomot és a koriandert.", "Stir in the chopped tomato and the coriander."),
                t("Süsd mindkét oldalát három-három percig forró serpenyőben.", "Cook it three minutes a side in a hot pan."),
            ),
            listOf(
                ing("gluténmentes csicseriborsóliszt", "chickpea flour", 90.0, "g", Aisle.SZARAZARU),
                ing("paradicsom", "tomato", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("friss koriander", "fresh coriander", 10.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("kurkuma", "turmeric", 2.0, "g", Aisle.FUSZER, staple = true),
                RAPESEED, SALT_PEPPER,
            ),
            kcal(440.0, 21.0, 55.0, 15.0, 10.0, 8.0),
        ),
        RecipeTemplate(
            t("Ful medames", "Ful medames"),
            t("Egyiptomi reggeli bab: laktató, növényi, olcsó.", "The Egyptian breakfast bean dish: filling, plant-based and cheap."),
            15,
            listOf(
                t("Melegítsd át a babot a saját levében, törd meg egy villával.", "Warm the beans in their liquid and crush them a little with a fork."),
                t("Ízesítsd citromlével, köménnyel és olívaolajjal.", "Season with lemon juice, cumin and olive oil."),
                t("Tedd a tetejére a paradicsomot, az uborkát és a petrezselymet.", "Top with the tomato, the cucumber and the parsley."),
            ),
            listOf(
                ing("főtt lóbab konzerv", "tinned fava beans", 250.0, "g", Aisle.SZARAZARU),
                ing("paradicsom", "tomato", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("uborka", "cucumber", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("citrom", "lemon", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("őrölt kömény", "ground cumin", 2.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(450.0, 22.0, 52.0, 16.0, 16.0, 6.0),
        ),
        RecipeTemplate(
            t("Menemen", "Menemen"),
            t("Török tojásos-paprikás serpenyős étel.", "The Turkish pan of eggs and peppers."),
            20,
            listOf(
                t("Pirítsd meg a paprikát olajon, amíg megpuhul.", "Fry the peppers in oil until soft."),
                t("Add hozzá a reszelt paradicsomot, főzd sűrűre.", "Add the grated tomato and cook it down."),
                t("Üsd bele a tojásokat, és lassan keverd össze.", "Crack in the eggs and stir them through slowly."),
            ),
            listOf(
                ing("tojás", "eggs", 3.0, "db", Aisle.TEJTERMEK),
                ing("zöldpaprika", "green pepper", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("paradicsom", "tomato", 200.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("pirospaprika", "sweet paprika", 2.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(420.0, 23.0, 20.0, 27.0, 5.0, 12.0),
        ),
        RecipeTemplate(
            t("Rizskása fahéjjal és körtével", "Rice pudding with cinnamon and pear"),
            t("Kínai és skandináv reggelik közös őse, itt növényi tejjel.", "A breakfast both China and Scandinavia know, here with plant milk."),
            30,
            listOf(
                t("Főzd a rizst a rizstejben lassú tűzön huszonöt percig.", "Simmer the rice in the rice milk for twenty-five minutes."),
                t("Kevergesd, hogy ne ragadjon le, adj hozzá vizet, ha sűrű.", "Stir so it does not catch, and add water if it thickens too much."),
                t("Keverd bele a fahéjat, tedd rá a párolt körtét.", "Stir in the cinnamon and top with the poached pear."),
            ),
            listOf(
                ing("rizs", "pudding rice", 70.0, "g", Aisle.SZARAZARU),
                ing("rizstej", "rice milk", 300.0, "ml", Aisle.ITAL),
                ing("körte", "pear", 1.0, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("fahéj", "cinnamon", 1.0, "g", Aisle.FUSZER, staple = true),
            ),
            kcal(430.0, 7.0, 92.0, 4.0, 5.0, 24.0),
        ),
        RecipeTemplate(
            t("Avokádós pirítós pocheolt tojással", "Avocado toast with a poached egg"),
            t("Ausztrál kávézók reggelije, húsz perc alatt.", "The Australian café breakfast, in twenty minutes."),
            15,
            listOf(
                t("Pirítsd meg a kenyeret.", "Toast the bread."),
                t("Villával törd össze az avokádót citromlével és sóval.", "Crush the avocado with lemon juice and salt."),
                t("Főzz buggyantott tojást enyhén forró vízben három percig.", "Poach the egg in barely simmering water for three minutes."),
                t("Kend a kenyérre az avokádót, tedd rá a tojást.", "Spread the avocado on the toast and set the egg on top."),
            ),
            listOf(
                ing("teljes kiőrlésű kenyér", "wholemeal bread", 70.0, "g", Aisle.PEKARU),
                ing("avokádó", "avocado", 1.0, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("tojás", "egg", 2.0, "db", Aisle.TEJTERMEK),
                ing("citrom", "lemon", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                SALT_PEPPER,
            ),
            kcal(490.0, 21.0, 38.0, 28.0, 10.0, 4.0),
        ),
        RecipeTemplate(
            t("Skyr bogyós gyümölccsel és tökmaggal", "Skyr with berries and pumpkin seeds"),
            t("Izlandi savanyított tejtermék: sok fehérje, alig zsír.", "The Icelandic cultured dairy: lots of protein, almost no fat."),
            5,
            listOf(
                t("Kanalazd tálba a skyrt.", "Spoon the skyr into a bowl."),
                t("Tedd rá a bogyós gyümölcsöt és a tökmagot.", "Add the berries and the pumpkin seeds."),
            ),
            listOf(
                ing("skyr", "skyr", 250.0, "g", Aisle.TEJTERMEK),
                ing("eper", "strawberries", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("tökmag", "pumpkin seeds", 20.0, "g", Aisle.SZARAZARU),
            ),
            kcal(400.0, 34.0, 30.0, 14.0, 5.0, 18.0),
        ),
        RecipeTemplate(
            t("Tofurántotta zöldségekkel", "Scrambled tofu with vegetables"),
            t("Vegán reggeli, ami tojás nélkül is tojásra emlékeztet.", "A vegan breakfast that eats like eggs without any."),
            15,
            listOf(
                t("Morzsold a tofut a serpenyőbe, pirítsd három percig.", "Crumble the tofu into the pan and fry it for three minutes."),
                t("Fűszerezd kurkumával, a színéért és az ízéért.", "Season it with turmeric, for the colour and the taste."),
                t("Add hozzá a paprikát és a spenótot, süsd össze.", "Add the pepper and the spinach and cook them through."),
            ),
            listOf(
                ing("füstölt tofu", "smoked tofu", 200.0, "g", Aisle.SZARAZARU),
                ing("kaliforniai paprika", "bell pepper", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("friss spenót", "spinach", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("kurkuma", "turmeric", 2.0, "g", Aisle.FUSZER, staple = true),
                RAPESEED, SALT_PEPPER,
            ),
            kcal(420.0, 32.0, 18.0, 25.0, 7.0, 6.0),
        ),
        RecipeTemplate(
            t("Chiapuding mandulatejjel", "Chia pudding with almond milk"),
            t("Este összeállítod, reggel kész. Vegán.", "You put it together at night and it is ready in the morning."),
            5,
            listOf(
                t("Keverd el a chia magot a mandulatejben.", "Stir the chia seeds into the almond milk."),
                t("Tedd hűtőbe legalább négy órára.", "Chill it for at least four hours."),
                t("Reggel keverd meg, tedd rá a gyümölcsöt.", "Stir it in the morning and top it with the fruit."),
            ),
            listOf(
                ing("chia mag", "chia seeds", 45.0, "g", Aisle.SZARAZARU),
                ing("mandulatej", "almond milk", 300.0, "ml", Aisle.ITAL),
                ing("áfonya", "blueberries", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("juharszirup", "maple syrup", 10.0, "ml", Aisle.SZARAZARU),
            ),
            kcal(430.0, 14.0, 42.0, 22.0, 16.0, 16.0),
        ),
        RecipeTemplate(
            t("Congee gyömbérrel és újhagymával", "Congee with ginger and spring onion"),
            t("Kínai rizskása — a világ egyik legkíméletesebb reggelije.", "The Chinese rice porridge — one of the gentlest breakfasts there is."),
            45,
            listOf(
                t("Főzd a rizst nyolcszoros mennyiségű vízben, lassú tűzön.", "Simmer the rice in eight times its volume of water."),
                t("Kevergesd negyven percig, amíg krémes nem lesz.", "Stir it for forty minutes until it turns creamy."),
                t("Tedd rá a reszelt gyömbért, az újhagymát és a főtt tojást.", "Top it with the grated ginger, the spring onion and the boiled egg."),
            ),
            listOf(
                ing("rizs", "short-grain rice", 70.0, "g", Aisle.SZARAZARU),
                ing("tojás", "egg", 2.0, "db", Aisle.TEJTERMEK),
                ing("gyömbér", "fresh ginger", 10.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("újhagyma", "spring onion", 30.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                SALT_PEPPER,
            ),
            kcal(430.0, 20.0, 58.0, 12.0, 3.0, 2.0),
        ),
        RecipeTemplate(
            t("Quinoás reggelitál sütőtökkel", "Quinoa breakfast bowl with squash"),
            t("Andoki gabona, magyar sütőtök. Vegán és gluténmentes.", "An Andean grain with Hungarian squash. Vegan and gluten-free."),
            30,
            listOf(
                t("Süsd meg a sütőtököt kockázva, húsz percig.", "Roast the diced squash for twenty minutes."),
                t("Főzd meg a quinoát sós vízben tizenöt perc alatt.", "Cook the quinoa in salted water for fifteen minutes."),
                t("Keverd össze, szórd meg tökmaggal és fahéjjal.", "Mix them and scatter over the pumpkin seeds and cinnamon."),
            ),
            listOf(
                ing("quinoa", "quinoa", 70.0, "g", Aisle.SZARAZARU),
                ing("sütőtök", "butternut squash", 250.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("tökmag", "pumpkin seeds", 20.0, "g", Aisle.SZARAZARU),
                ing("fahéj", "cinnamon", 1.0, "g", Aisle.FUSZER, staple = true),
                OIL,
            ),
            kcal(450.0, 14.0, 66.0, 15.0, 10.0, 12.0),
        ),
        RecipeTemplate(
            t("Spanyol paradicsomos kenyér sonkával", "Pan con tomate with ham"),
            t("Katalán reggeli: kenyér, paradicsom, olaj, semmi trükk.", "The Catalan breakfast: bread, tomato, oil, no tricks."),
            8,
            listOf(
                t("Pirítsd meg a kenyeret, dörzsöld be fokhagymával.", "Toast the bread and rub it with garlic."),
                t("Reszeld rá a paradicsomot, locsold meg olívaolajjal.", "Grate the tomato over it and trickle on the olive oil."),
                t("Tedd rá a sonkát.", "Lay the ham on top."),
            ),
            listOf(
                ing("teljes kiőrlésű kenyér", "wholemeal bread", 80.0, "g", Aisle.PEKARU),
                ing("paradicsom", "tomato", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("sonka", "cured ham", 60.0, "g", Aisle.HUS_HAL),
                ing("fokhagyma", "garlic", 1.0, "gerezd", Aisle.ZOLDSEG_GYUMOLCS),
                OIL,
            ),
            kcal(450.0, 24.0, 46.0, 18.0, 6.0, 6.0),
        ),
        RecipeTemplate(
            t("Tükörtojás sült édesburgonyával", "Fried eggs with roast sweet potato"),
            t("Gluténmentes, tejtermék nélküli, laktató reggeli.", "A gluten-free, dairy-free breakfast that keeps you going."),
            30,
            listOf(
                t("Süsd meg a kockára vágott édesburgonyát húsz percig.", "Roast the diced sweet potato for twenty minutes."),
                t("Süss rá két tükörtojást.", "Fry two eggs to go with it."),
                t("Szórd meg pirospaprikával és friss petrezselyemmel.", "Dust it with paprika and scatter over the parsley."),
            ),
            listOf(
                ing("édesburgonya", "sweet potato", 280.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("tojás", "eggs", 2.0, "db", Aisle.TEJTERMEK),
                ing("petrezselyem", "parsley", 10.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("pirospaprika", "sweet paprika", 2.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(470.0, 19.0, 58.0, 18.0, 8.0, 14.0),
        ),
        RecipeTemplate(
            t("Kölesgombóc almakompóttal", "Millet porridge with stewed apple"),
            t("Régi magyar gabona, ma gluténmentes reggeli.", "An old Hungarian grain, today a gluten-free breakfast."),
            25,
            listOf(
                t("Öblítsd le a kölest, főzd háromszoros vízben húsz percig.", "Rinse the millet and cook it in three times its water for twenty minutes."),
                t("Párold meg az almát fahéjjal.", "Stew the apple with the cinnamon."),
                t("Keverd össze, locsold meg juharsziruppal.", "Combine them and trickle over the maple syrup."),
            ),
            listOf(
                ing("köles", "millet", 70.0, "g", Aisle.SZARAZARU),
                ing("alma", "apple", 200.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("juharszirup", "maple syrup", 10.0, "ml", Aisle.SZARAZARU),
                ing("fahéj", "cinnamon", 1.0, "g", Aisle.FUSZER, staple = true),
            ),
            kcal(420.0, 9.0, 88.0, 4.0, 7.0, 26.0),
        ),
        RecipeTemplate(
            t("Vietnami zöldséges rizstésztaleves reggelire", "Vietnamese rice noodle soup for breakfast"),
            t("Hanoiban a leves a reggeli. Vegán és gluténmentes.", "In Hanoi, soup is breakfast. Vegan and gluten-free."),
            25,
            listOf(
                t("Forralj alaplevet gyömbérrel, csillagánizzsal és fahéjjal.", "Bring the stock to the boil with ginger, star anise and cinnamon."),
                t("Áztasd be a gluténmentes rizstésztát forró vízbe öt percre.", "Soak the rice noodles in hot water for five minutes."),
                t("Szedd tálba, öntsd rá a levest, tedd rá a zöldségeket és a csírát.", "Put them in a bowl, pour the broth over, then add the vegetables and the sprouts."),
            ),
            listOf(
                ing("gluténmentes rizstészta", "rice noodles", 80.0, "g", Aisle.SZARAZARU),
                ing("zöldségalaplé", "vegetable stock", 500.0, "ml", Aisle.SZARAZARU),
                ing("babcsíra", "bean sprouts", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("gyömbér", "fresh ginger", 10.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("friss koriander", "fresh coriander", 10.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
            ),
            kcal(400.0, 10.0, 82.0, 3.0, 4.0, 4.0),
        ),
        RecipeTemplate(
            t("Sajtos-zöldséges frittata", "Vegetable frittata"),
            t("Olasz sült omlett — hidegen is jó, másnap is.", "The Italian baked omelette — good cold, and good the next day."),
            25,
            listOf(
                t("Pirítsd meg a cukkinit és a paprikát serpenyőben.", "Fry the courgette and the pepper in a pan."),
                t("Verd fel a tojásokat a reszelt sajttal, sózd.", "Beat the eggs with the grated cheese and season them."),
                t("Öntsd a zöldségre, süsd készre lassú tűzön vagy sütőben.", "Pour it over the vegetables and cook it through on a low heat or in the oven."),
            ),
            listOf(
                ing("tojás", "eggs", 3.0, "db", Aisle.TEJTERMEK),
                ing("cukkini", "courgette", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("kaliforniai paprika", "bell pepper", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("reszelt sajt", "grated cheese", 40.0, "g", Aisle.TEJTERMEK),
                OIL, SALT_PEPPER,
            ),
            kcal(460.0, 31.0, 12.0, 32.0, 4.0, 8.0),
        ),
        RecipeTemplate(
            t("Lencsés reggelitál lime-mal", "Breakfast lentil bowl with lime"),
            t("Dél-indiai szokás: a reggeli is lehet sós és hüvelyes.", "A South Indian habit: breakfast can be savoury and full of pulses."),
            25,
            listOf(
                t("Főzd meg a vörös lencsét húsz perc alatt puhára.", "Simmer the red lentils for twenty minutes until soft."),
                t("Pirítsd meg a mustármagot és a köményt olajban, öntsd a lencsére.", "Toast the mustard seeds and cumin in oil and pour them over the lentils."),
                t("Csavarj rá lime-ot, szórd meg korianderrel.", "Squeeze the lime over and scatter on the coriander."),
            ),
            listOf(
                ing("vörös lencse", "red lentils", 90.0, "g", Aisle.SZARAZARU),
                ing("lime", "lime", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("mustármag", "mustard seeds", 3.0, "g", Aisle.FUSZER, staple = true),
                ing("friss koriander", "fresh coriander", 10.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                RAPESEED, SALT_PEPPER,
            ),
            kcal(420.0, 24.0, 58.0, 10.0, 15.0, 4.0),
        ),
    )

    // =============================================================================
    // FŐÉTEL (ebéd és vacsora)
    // =============================================================================

    val MAINS: List<RecipeTemplate> = listOf(
        RecipeTemplate(
            t("Grillezett csirkemell párolt rizzsel és salátával", "Grilled chicken breast with rice and salad"),
            t("Egyszerű, kiszámítható alap fogás.", "A simple, dependable staple."),
            25,
            listOf(
                t("Fűszerezd a csirkemellet, és süsd grillserpenyőben oldalanként hat percig.", "Season the chicken breast and grill it six minutes a side."),
                t("Főzd meg a rizst sós vízben.", "Cook the rice in salted water."),
                t("Keverj salátát olívaolajjal és citrommal.", "Dress the salad with olive oil and lemon."),
            ),
            listOf(
                ing("csirkemell", "chicken breast", 180.0, "g", Aisle.HUS_HAL),
                ing("barna rizs", "brown rice", 70.0, "g", Aisle.SZARAZARU),
                ing("vegyes saláta", "mixed salad leaves", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(620.0, 50.0, 60.0, 17.0, 6.0, 4.0),
        ),
        RecipeTemplate(
            t("Sült lazac édesburgonyával és brokkolival", "Baked salmon with sweet potato and broccoli"),
            t("Omega-3-ban gazdag főétel.", "A main course rich in omega-3."),
            30,
            listOf(
                t("Süsd a lazacot 180 fokon tizenöt percig.", "Bake the salmon at 180°C for fifteen minutes."),
                t("Süsd meg mellette a kockázott édesburgonyát.", "Roast the diced sweet potato alongside."),
                t("Párold a brokkolit öt percig, hogy ropogós maradjon.", "Steam the broccoli for five minutes so it stays crisp."),
            ),
            listOf(
                ing("lazacfilé", "salmon fillet", 160.0, "g", Aisle.HUS_HAL),
                ing("édesburgonya", "sweet potato", 250.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("brokkoli", "broccoli", 200.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(640.0, 40.0, 55.0, 27.0, 9.0, 12.0),
        ),
        RecipeTemplate(
            t("Bolognai lencseragu teljes kiőrlésű tésztával", "Lentil bolognese with wholemeal pasta"),
            t("Növényi fehérje, sok rost.", "Plant protein and plenty of fibre."),
            30,
            listOf(
                t("Dinszteld a hagymát és a sárgarépát olajon.", "Soften the onion and the carrot in oil."),
                t("Add hozzá a lencsét és a paradicsomot, főzd húsz percig.", "Add the lentils and the tomatoes and simmer for twenty minutes."),
                t("Főzd ki a tésztát, forgasd össze a raguval.", "Cook the pasta and toss it through the sauce."),
            ),
            listOf(
                ing("vörös lencse", "red lentils", 90.0, "g", Aisle.SZARAZARU),
                ing("teljes kiőrlésű tészta", "wholemeal pasta", 80.0, "g", Aisle.SZARAZARU),
                ing("paradicsomkonzerv", "tinned tomatoes", 200.0, "g", Aisle.SZARAZARU),
                ing("vöröshagyma", "onion", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("sárgarépa", "carrot", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                OIL,
            ),
            kcal(610.0, 32.0, 100.0, 8.0, 18.0, 12.0),
        ),
        RecipeTemplate(
            t("Marhapörkölt párolt zöldségekkel", "Beef stew with braised vegetables"),
            t("Hétvégi, laktató magyar fogás.", "A hearty Hungarian weekend dish."),
            75,
            listOf(
                t("Pirítsd üvegesre a hagymát zsiradékon.", "Fry the onion until it turns glassy."),
                t("Vedd le a tűzről, keverd bele a pirospaprikát, majd add hozzá a húst.", "Take it off the heat, stir in the paprika, then add the beef."),
                t("Főzd lassú tűzön egy órán át, amíg omlós nem lesz.", "Simmer it for an hour until it falls apart."),
                t("Párold mellé a burgonyát és a paprikát.", "Braise the potato and the pepper alongside."),
            ),
            listOf(
                ing("marhalábszár", "beef shin", 180.0, "g", Aisle.HUS_HAL),
                ing("burgonya", "potato", 200.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("vöröshagyma", "onion", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("kaliforniai paprika", "bell pepper", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("pirospaprika", "sweet paprika", 5.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(650.0, 45.0, 48.0, 28.0, 7.0, 8.0),
        ),
        RecipeTemplate(
            t("Csirkés-zöldséges wok basmati rizzsel", "Chicken and vegetable stir-fry with basmati rice"),
            t("Egy serpenyős, húszperces vacsora.", "A one-pan dinner in twenty minutes."),
            20,
            listOf(
                t("Pirítsd a csirkét magas lángon három percig.", "Sear the chicken over a high flame for three minutes."),
                t("Dobd hozzá a zöldségeket, rázd át kétpercenként.", "Throw in the vegetables and toss them every couple of minutes."),
                t("Ízesítsd szójaszósszal, tálald a rizzsel.", "Season with soy sauce and serve with the rice."),
            ),
            listOf(
                ing("csirkecomb filé", "chicken thigh fillet", 170.0, "g", Aisle.HUS_HAL),
                ing("wok zöldségkeverék", "stir-fry vegetable mix", 250.0, "g", Aisle.FAGYASZTOTT),
                ing("basmati rizs", "basmati rice", 70.0, "g", Aisle.SZARAZARU),
                ing("szójaszósz", "soy sauce", 15.0, "ml", Aisle.FUSZER, staple = true),
                RAPESEED,
            ),
            kcal(630.0, 43.0, 68.0, 18.0, 8.0, 9.0),
        ),
        RecipeTemplate(
            t("Chana masala basmati rizzsel", "Chana masala with basmati rice"),
            t("Észak-indiai csicseriborsó-curry. Vegán és gluténmentes.", "The North Indian chickpea curry. Vegan and gluten-free."),
            30,
            listOf(
                t("Pirítsd a hagymát, a fokhagymát és a gyömbért olajon öt percig.", "Fry the onion, garlic and ginger in oil for five minutes."),
                t("Add hozzá a garam masalát, a kurkumát és a köményt.", "Add the garam masala, the turmeric and the cumin."),
                t("Öntsd hozzá a paradicsomot és a csicseriborsót, főzd húsz percig.", "Pour in the tomatoes and the chickpeas and simmer for twenty minutes."),
                t("Csavarj rá citromot, tálald a rizzsel.", "Squeeze lemon over it and serve with the rice."),
            ),
            listOf(
                ing("csicseriborsó konzerv", "tinned chickpeas", 250.0, "g", Aisle.SZARAZARU),
                ing("basmati rizs", "basmati rice", 70.0, "g", Aisle.SZARAZARU),
                ing("paradicsomkonzerv", "tinned tomatoes", 200.0, "g", Aisle.SZARAZARU),
                ing("vöröshagyma", "onion", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("garam masala", "garam masala", 5.0, "g", Aisle.FUSZER, staple = true),
                RAPESEED, SALT_PEPPER,
            ),
            kcal(640.0, 24.0, 102.0, 15.0, 18.0, 12.0),
        ),
        RecipeTemplate(
            t("Vöröslencse-dhal rizzsel", "Red lentil dhal with rice"),
            t("Dél-indiai alapétel: vegán, gluténmentes, diómentes, szójamentes.", "A South Indian staple: vegan, gluten-free, nut-free and soy-free."),
            30,
            listOf(
                t("Főzd a vörös lencsét vízben tizenöt percig, míg szét nem esik.", "Simmer the red lentils in water for fifteen minutes until they collapse."),
                t("Pirítsd a köményt, a mustármagot és a kurkumát olajban egy percig.", "Toast the cumin, mustard seeds and turmeric in oil for a minute."),
                t("Öntsd a fűszeres olajat a lencsére, keverd el.", "Pour the spiced oil into the lentils and stir it through."),
                t("Tálald főtt rizzsel és friss korianderrel.", "Serve it with boiled rice and fresh coriander."),
            ),
            listOf(
                ing("vörös lencse", "red lentils", 100.0, "g", Aisle.SZARAZARU),
                ing("basmati rizs", "basmati rice", 70.0, "g", Aisle.SZARAZARU),
                ing("gyömbér", "fresh ginger", 10.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("kurkuma", "turmeric", 3.0, "g", Aisle.FUSZER, staple = true),
                ing("friss koriander", "fresh coriander", 10.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                RAPESEED, SALT_PEPPER,
            ),
            kcal(620.0, 28.0, 104.0, 11.0, 16.0, 4.0),
        ),
        RecipeTemplate(
            t("Chili sin carne", "Chili sin carne"),
            t("Mexikói ihletésű bab- és kukoricaragu hús nélkül.", "A Mexican-inspired bean and corn stew without the meat."),
            35,
            listOf(
                t("Pirítsd a hagymát és a paprikát olajon.", "Fry the onion and the pepper in oil."),
                t("Add hozzá a chilit, a köményt és a füstölt pirospaprikát.", "Add the chilli, the cumin and the smoked paprika."),
                t("Öntsd hozzá a babot, a kukoricát és a paradicsomot, főzd húsz percig.", "Tip in the beans, the sweetcorn and the tomatoes and simmer for twenty minutes."),
                t("Tálald főtt rizzsel vagy magában.", "Serve it with rice or on its own."),
            ),
            listOf(
                ing("vörösbab konzerv", "tinned kidney beans", 250.0, "g", Aisle.SZARAZARU),
                ing("kukorica konzerv", "tinned sweetcorn", 100.0, "g", Aisle.SZARAZARU),
                ing("paradicsomkonzerv", "tinned tomatoes", 250.0, "g", Aisle.SZARAZARU),
                ing("kaliforniai paprika", "bell pepper", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("barna rizs", "brown rice", 60.0, "g", Aisle.SZARAZARU),
                OIL, SALT_PEPPER,
            ),
            kcal(610.0, 26.0, 102.0, 12.0, 22.0, 14.0),
        ),
        RecipeTemplate(
            t("Thai zöld curry csirkével", "Thai green curry with chicken"),
            t("Kókusztejes, csípős curry rizzsel. Tejtermék nélkül.", "A coconut curry with a kick, served with rice. Dairy-free."),
            30,
            listOf(
                t("Pirítsd meg a zöld currypasztát olajon egy percig.", "Fry the green curry paste in oil for a minute."),
                t("Add hozzá a csirkét, forgasd át.", "Add the chicken and turn it through."),
                t("Öntsd fel kókusztejjel, tedd bele a zöldségeket, főzd tizenöt percig.", "Pour in the coconut milk, add the vegetables and simmer for fifteen minutes."),
                t("Csavarj rá lime-ot, tálald jázminrizzsel.", "Squeeze lime over it and serve with jasmine rice."),
            ),
            listOf(
                ing("csirkemell", "chicken breast", 170.0, "g", Aisle.HUS_HAL),
                ing("kókusztej", "coconut milk", 150.0, "ml", Aisle.SZARAZARU),
                ing("zöld currypaszta", "green curry paste", 25.0, "g", Aisle.FUSZER),
                ing("jázminrizs", "jasmine rice", 70.0, "g", Aisle.SZARAZARU),
                ing("zöldbab", "green beans", 120.0, "g", Aisle.FAGYASZTOTT),
                ing("lime", "lime", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
            ),
            kcal(660.0, 44.0, 70.0, 24.0, 6.0, 8.0),
        ),
        RecipeTemplate(
            t("Marokkói zöldségtagine kuszkusszal", "Moroccan vegetable tagine with couscous"),
            t("Fűszeres, édeskés észak-afrikai egytálétel.", "A spiced, faintly sweet North African one-pot."),
            40,
            listOf(
                t("Pirítsd a hagymát, add hozzá a fahéjat, a köményt és a kurkumát.", "Fry the onion, then add the cinnamon, cumin and turmeric."),
                t("Tedd bele a sárgarépát, a cukkinit és a csicseriborsót.", "Add the carrot, the courgette and the chickpeas."),
                t("Öntsd fel alaplével, főzd huszonöt percig.", "Pour in the stock and cook for twenty-five minutes."),
                t("Forrázd le a kuszkuszt, tálald a tagine mellé.", "Steep the couscous and serve it alongside."),
            ),
            listOf(
                ing("kuszkusz", "couscous", 70.0, "g", Aisle.SZARAZARU),
                ing("csicseriborsó konzerv", "tinned chickpeas", 200.0, "g", Aisle.SZARAZARU),
                ing("sárgarépa", "carrot", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("cukkini", "courgette", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("zöldségalaplé", "vegetable stock", 300.0, "ml", Aisle.SZARAZARU),
                OIL, SALT_PEPPER,
            ),
            kcal(600.0, 22.0, 96.0, 14.0, 17.0, 14.0),
        ),
        RecipeTemplate(
            t("Görög csirkés tál tzatzikivel", "Greek chicken bowl with tzatziki"),
            t("Gyros ízek kenyér nélkül, gluténmentesen.", "Gyros flavours without the bread, and gluten-free."),
            30,
            listOf(
                t("Forgasd a csirkét oregánóba, fokhagymába és citromba, süsd meg.", "Toss the chicken in oregano, garlic and lemon, then cook it."),
                t("Keverj tzatzikit joghurtból, reszelt uborkából és fokhagymából.", "Stir the yoghurt, grated cucumber and garlic into a tzatziki."),
                t("Tálald főtt rizzsel, paradicsommal és lilahagymával.", "Serve it with rice, tomato and red onion."),
            ),
            listOf(
                ing("csirkemell", "chicken breast", 180.0, "g", Aisle.HUS_HAL),
                ing("görög joghurt", "Greek yoghurt", 120.0, "g", Aisle.TEJTERMEK),
                ing("uborka", "cucumber", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("basmati rizs", "basmati rice", 70.0, "g", Aisle.SZARAZARU),
                ing("oregánó", "oregano", 2.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(640.0, 52.0, 66.0, 17.0, 4.0, 8.0),
        ),
        RecipeTemplate(
            t("Töltött paprika paradicsomszósszal", "Stuffed peppers in tomato sauce"),
            t("Magyar klasszikus, darált pulykahússal könnyítve.", "A Hungarian classic, lightened with turkey mince."),
            60,
            listOf(
                t("Keverd össze a darált húst a főtt rizzsel, sózd, borsozd.", "Mix the mince with the cooked rice and season it."),
                t("Töltsd meg vele a kimagozott paprikákat.", "Stuff the cored peppers with it."),
                t("Főzd paradicsomszószban negyvenöt percig.", "Simmer them in the tomato sauce for forty-five minutes."),
            ),
            listOf(
                ing("darált pulykahús", "turkey mince", 180.0, "g", Aisle.HUS_HAL),
                ing("kaliforniai paprika", "bell peppers", 300.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("rizs", "rice", 50.0, "g", Aisle.SZARAZARU),
                ing("paradicsomlé", "passata", 300.0, "ml", Aisle.SZARAZARU),
                OIL, SALT_PEPPER,
            ),
            kcal(620.0, 46.0, 62.0, 20.0, 8.0, 18.0),
        ),
        RecipeTemplate(
            t("Sült pisztráng petrezselymes burgonyával", "Baked trout with parsley potatoes"),
            t("Egész halat sütni egyszerűbb, mint hinnéd.", "Cooking a whole fish is easier than it sounds."),
            35,
            listOf(
                t("Sózd be a halat kívül-belül, tegyél bele citromkarikát.", "Salt the fish inside and out and slip lemon slices in."),
                t("Süsd 200 fokon húsz percig.", "Bake it at 200°C for twenty minutes."),
                t("Főzd meg a burgonyát, forgasd petrezselyembe és olajba.", "Boil the potatoes and toss them in parsley and oil."),
            ),
            listOf(
                ing("pisztráng", "trout", 250.0, "g", Aisle.HUS_HAL),
                ing("burgonya", "potato", 300.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("petrezselyem", "parsley", 15.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("citrom", "lemon", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(610.0, 45.0, 58.0, 20.0, 6.0, 4.0),
        ),
        RecipeTemplate(
            t("Bibimbap", "Bibimbap"),
            t("Koreai rizstál zöldségekkel és tojással.", "The Korean rice bowl with vegetables and an egg."),
            35,
            listOf(
                t("Főzd meg a rizst, tedd egy tál aljára.", "Cook the rice and put it in the bottom of a bowl."),
                t("Pirítsd külön a sárgarépát, a spenótot és a gombát.", "Fry the carrot, the spinach and the mushrooms separately."),
                t("Rendezd a zöldségeket a rizs tetejére sávokban.", "Arrange the vegetables over the rice in stripes."),
                t("Tégy rá egy tükörtojást és egy kanál gochujangot.", "Top it with a fried egg and a spoon of gochujang."),
            ),
            listOf(
                ing("rizs", "short-grain rice", 80.0, "g", Aisle.SZARAZARU),
                ing("tojás", "egg", 1.0, "db", Aisle.TEJTERMEK),
                ing("sárgarépa", "carrot", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("friss spenót", "spinach", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("gomba", "mushrooms", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("gochujang paszta", "gochujang paste", 20.0, "g", Aisle.FUSZER),
                RAPESEED,
            ),
            kcal(600.0, 22.0, 92.0, 16.0, 8.0, 10.0),
        ),
        RecipeTemplate(
            t("Sült tofu párolt zöldséggel és rizzsel", "Baked tofu with steamed vegetables and rice"),
            t("Vegán, gluténmentes főétel, ami tényleg jóllakat.", "A vegan, gluten-free main that actually fills you up."),
            30,
            listOf(
                t("Nyomkodd ki a tofuból a vizet, vágd kockára.", "Press the water out of the tofu and cut it into cubes."),
                t("Forgasd tamariba és keményítőbe, süsd ropogósra húsz percig.", "Toss it in tamari and cornflour and bake it crisp for twenty minutes."),
                t("Párold a brokkolit és a sárgarépát, tálald a rizzsel.", "Steam the broccoli and carrot and serve with the rice."),
            ),
            listOf(
                ing("tofu", "firm tofu", 220.0, "g", Aisle.SZARAZARU),
                ing("barna rizs", "brown rice", 70.0, "g", Aisle.SZARAZARU),
                ing("brokkoli", "broccoli", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("sárgarépa", "carrot", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("tamari szósz", "tamari", 15.0, "ml", Aisle.FUSZER, staple = true),
                RAPESEED,
            ),
            kcal(600.0, 34.0, 74.0, 20.0, 10.0, 8.0),
        ),
        RecipeTemplate(
            t("Spanyol krumplis omlett", "Spanish potato tortilla"),
            t("Tortilla de patatas: három hozzávaló, végtelen türelem.", "Tortilla de patatas: three ingredients and a lot of patience."),
            40,
            listOf(
                t("Süsd a vékonyra vágott burgonyát és hagymát olajon puhára.", "Cook the thinly sliced potato and onion in oil until soft."),
                t("Keverd a felvert tojásokhoz, hagyd állni öt percet.", "Fold them into the beaten eggs and leave it five minutes."),
                t("Süsd serpenyőben lassan, fordítsd meg egy tányér segítségével.", "Cook it slowly in a pan and flip it with the help of a plate."),
            ),
            listOf(
                ing("tojás", "eggs", 4.0, "db", Aisle.TEJTERMEK),
                ing("burgonya", "potato", 300.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("vöröshagyma", "onion", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(620.0, 30.0, 58.0, 30.0, 6.0, 6.0),
        ),
        RecipeTemplate(
            t("Paella de verduras", "Paella de verduras"),
            t("Valenciai zöldségpaella sáfránnyal. Vegán és gluténmentes.", "Valencian vegetable paella with saffron. Vegan and gluten-free."),
            40,
            listOf(
                t("Pirítsd a paprikát és a zöldbabot olajon.", "Fry the pepper and the green beans in oil."),
                t("Add hozzá a rizst és a sáfrányt, forgasd át.", "Add the rice and the saffron and turn it through."),
                t("Öntsd fel forró alaplével, és NE keverd többet.", "Pour in hot stock and then do not stir it again."),
                t("Főzd húsz percig, hagyd pihenni ötöt.", "Cook it for twenty minutes and let it rest for five."),
            ),
            listOf(
                ing("paellarizs", "paella rice", 90.0, "g", Aisle.SZARAZARU),
                ing("kaliforniai paprika", "bell pepper", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("zöldbab", "green beans", 150.0, "g", Aisle.FAGYASZTOTT),
                ing("zöldségalaplé", "vegetable stock", 400.0, "ml", Aisle.SZARAZARU),
                ing("sáfrány", "saffron", 1.0, "csipet", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(580.0, 14.0, 106.0, 12.0, 9.0, 10.0),
        ),
        RecipeTemplate(
            t("Ratatouille sült csirkecombbal", "Ratatouille with roast chicken thigh"),
            t("Provence-i zöldségragu, mellé sült comb.", "The Provençal vegetable stew with a roast thigh beside it."),
            50,
            listOf(
                t("Vágd kockára a padlizsánt, a cukkinit és a paprikát.", "Dice the aubergine, the courgette and the pepper."),
                t("Süsd őket olajon külön-külön, majd forgasd össze paradicsommal.", "Cook them separately in oil, then bring them together with the tomato."),
                t("Süsd a csirkecombot 200 fokon harmincöt percig.", "Roast the chicken thigh at 200°C for thirty-five minutes."),
            ),
            listOf(
                ing("csirkecomb", "chicken thigh", 200.0, "g", Aisle.HUS_HAL),
                ing("padlizsán", "aubergine", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("cukkini", "courgette", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("kaliforniai paprika", "bell pepper", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("paradicsom", "tomato", 200.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(640.0, 42.0, 30.0, 38.0, 11.0, 18.0),
        ),
        RecipeTemplate(
            t("Falafel tál tahinivel", "Falafel bowl with tahini"),
            t("Levantei, vegán, gluténmentes, ha nem kerül mellé pita.", "Levantine, vegan and gluten-free as long as no pita turns up."),
            35,
            listOf(
                t("Turmixold össze az áztatott csicseriborsót a fűszerekkel és a petrezselyemmel.", "Blitz the soaked chickpeas with the spices and the parsley."),
                t("Formázz golyókat, süsd sütőben huszonöt percig.", "Shape it into balls and bake them for twenty-five minutes."),
                t("Keverj tahiniszószt citrommal és vízzel.", "Loosen the tahini with lemon and water into a sauce."),
                t("Tálald salátával és paradicsommal.", "Serve with salad and tomato."),
            ),
            listOf(
                ing("szárított csicseriborsó", "dried chickpeas", 120.0, "g", Aisle.SZARAZARU),
                ing("tahini", "tahini", 30.0, "g", Aisle.SZARAZARU),
                ing("petrezselyem", "parsley", 20.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("paradicsom", "tomato", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("citrom", "lemon", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(620.0, 27.0, 72.0, 26.0, 21.0, 8.0),
        ),
        RecipeTemplate(
            t("Perzsa lencsés rizs", "Persian lentil rice"),
            t("Adas polo: rizs, lencse, fahéj. Vegán és gluténmentes.", "Adas polo: rice, lentils and cinnamon. Vegan and gluten-free."),
            40,
            listOf(
                t("Főzd meg külön a rizst és a barna lencsét.", "Cook the rice and the brown lentils separately."),
                t("Pirítsd a hagymát aranybarnára, fűszerezd fahéjjal és kurkumával.", "Fry the onion golden and season it with cinnamon and turmeric."),
                t("Rétegezd össze a rizst, a lencsét és a hagymát, párold tíz percig.", "Layer the rice, lentils and onion and steam it for ten minutes."),
            ),
            listOf(
                ing("basmati rizs", "basmati rice", 80.0, "g", Aisle.SZARAZARU),
                ing("barna lencse", "brown lentils", 80.0, "g", Aisle.SZARAZARU),
                ing("vöröshagyma", "onion", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("fahéj", "cinnamon", 2.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(610.0, 22.0, 104.0, 12.0, 14.0, 6.0),
        ),
        RecipeTemplate(
            t("Nasi goreng csirkével", "Nasi goreng with chicken"),
            t("Indonéz sült rizs: a tegnapi rizs legjobb sorsa.", "Indonesian fried rice: the best fate for yesterday's rice."),
            20,
            listOf(
                t("Pirítsd a csirkét és a zöldségeket forró olajon.", "Fry the chicken and the vegetables in hot oil."),
                t("Add hozzá a hideg főtt rizst, pirítsd át.", "Add the cold cooked rice and fry it through."),
                t("Ízesítsd szójaszósszal, tegyél rá tükörtojást.", "Season with soy sauce and set a fried egg on top."),
            ),
            listOf(
                ing("csirkemell", "chicken breast", 150.0, "g", Aisle.HUS_HAL),
                ing("főtt rizs", "cooked rice", 200.0, "g", Aisle.SZARAZARU),
                ing("tojás", "egg", 1.0, "db", Aisle.TEJTERMEK),
                ing("wok zöldségkeverék", "stir-fry vegetable mix", 150.0, "g", Aisle.FAGYASZTOTT),
                ing("szójaszósz", "soy sauce", 20.0, "ml", Aisle.FUSZER, staple = true),
                RAPESEED,
            ),
            kcal(650.0, 45.0, 72.0, 20.0, 5.0, 6.0),
        ),
        RecipeTemplate(
            t("Sült tőkehal fehérbabbal és spenóttal", "Baked cod with white beans and spinach"),
            t("Ibériai páros: fehér hal és puha bab.", "An Iberian pairing: white fish and soft beans."),
            30,
            listOf(
                t("Süsd a tőkehalat 190 fokon tizenöt percig.", "Bake the cod at 190°C for fifteen minutes."),
                t("Melegítsd át a fehérbabot fokhagymás olajon.", "Warm the white beans through in garlic oil."),
                t("Forgasd bele a spenótot, amíg össze nem esik.", "Wilt the spinach into them."),
            ),
            listOf(
                ing("tőkehalfilé", "cod fillet", 200.0, "g", Aisle.HUS_HAL),
                ing("fehérbab konzerv", "tinned white beans", 250.0, "g", Aisle.SZARAZARU),
                ing("friss spenót", "spinach", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("fokhagyma", "garlic", 2.0, "gerezd", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(600.0, 52.0, 52.0, 18.0, 16.0, 4.0),
        ),
        RecipeTemplate(
            t("Gombás rizottó", "Mushroom risotto"),
            t("Észak-olasz vacsora, húsz perc kevergetés.", "A northern Italian dinner and twenty minutes of stirring."),
            35,
            listOf(
                t("Pirítsd a gombát magas lángon, tedd félre.", "Fry the mushrooms over a high flame and set them aside."),
                t("Dinszteld a hagymát, add hozzá a rizst, pirítsd üvegesre.", "Soften the onion, add the rice and toast it until glassy."),
                t("Merj hozzá forró alaplevet kanalanként, kevergesd húsz percig.", "Add hot stock a ladle at a time and stir for twenty minutes."),
                t("Keverd bele a gombát és a reszelt sajtot.", "Stir the mushrooms and the grated cheese back in."),
            ),
            listOf(
                ing("rizottórizs", "risotto rice", 90.0, "g", Aisle.SZARAZARU),
                ing("gomba", "mushrooms", 250.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("zöldségalaplé", "vegetable stock", 500.0, "ml", Aisle.SZARAZARU),
                ing("reszelt sajt", "grated cheese", 30.0, "g", Aisle.TEJTERMEK),
                ing("vöröshagyma", "onion", 60.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(620.0, 20.0, 92.0, 20.0, 6.0, 6.0),
        ),
        RecipeTemplate(
            t("Töltött édesburgonya feketebabbal", "Stuffed sweet potato with black beans"),
            t("Mexikói ízek, vegán, gluténmentes, diómentes.", "Mexican flavours: vegan, gluten-free and nut-free."),
            50,
            listOf(
                t("Süsd egészben az édesburgonyát 200 fokon negyven percig.", "Bake the sweet potatoes whole at 200°C for forty minutes."),
                t("Melegítsd a feketebabot köménnyel és füstölt paprikával.", "Warm the black beans with cumin and smoked paprika."),
                t("Vágd fel az édesburgonyát, töltsd meg, tedd rá az avokádót és a lime-ot.", "Split the sweet potatoes, fill them and add the avocado and lime."),
            ),
            listOf(
                ing("édesburgonya", "sweet potato", 350.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("feketebab konzerv", "tinned black beans", 250.0, "g", Aisle.SZARAZARU),
                ing("avokádó", "avocado", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("lime", "lime", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("őrölt kömény", "ground cumin", 3.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(620.0, 22.0, 96.0, 18.0, 24.0, 20.0),
        ),
        RecipeTemplate(
            t("Lazacos-avokádós quinoatál", "Salmon and avocado quinoa bowl"),
            t("Gluténmentes, tejtermék nélküli, tizenöt perc alatt kész.", "Gluten-free, dairy-free and ready in fifteen minutes."),
            20,
            listOf(
                t("Főzd meg a quinoát tizenöt perc alatt.", "Cook the quinoa in fifteen minutes."),
                t("Süsd a lazacot serpenyőben, bőrével lefelé.", "Sear the salmon skin-side down in a pan."),
                t("Rakd össze a tálat quinoával, lazaccal, avokádóval és uborkával.", "Build the bowl with quinoa, salmon, avocado and cucumber."),
            ),
            listOf(
                ing("lazacfilé", "salmon fillet", 150.0, "g", Aisle.HUS_HAL),
                ing("quinoa", "quinoa", 70.0, "g", Aisle.SZARAZARU),
                ing("avokádó", "avocado", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("uborka", "cucumber", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("citrom", "lemon", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(650.0, 40.0, 56.0, 30.0, 10.0, 4.0),
        ),
        RecipeTemplate(
            t("Sült pulykamell sütőtökkel és quinoával", "Roast turkey breast with squash and quinoa"),
            t("Amerikai ihletésű őszi tál. Tejtermék nélkül.", "An American-leaning autumn plate. Dairy-free."),
            40,
            listOf(
                t("Süsd a sütőtököt rozmaringgal harminc percig.", "Roast the squash with rosemary for thirty minutes."),
                t("Süsd a pulykamellet serpenyőben, majd pihentesd öt percet.", "Sear the turkey breast and let it rest for five minutes."),
                t("Főzd meg a quinoát, keverd össze mindennel.", "Cook the quinoa and bring it all together."),
            ),
            listOf(
                ing("pulykamell", "turkey breast", 180.0, "g", Aisle.HUS_HAL),
                ing("sütőtök", "butternut squash", 250.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("quinoa", "quinoa", 70.0, "g", Aisle.SZARAZARU),
                ing("rozmaring", "rosemary", 3.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(630.0, 52.0, 62.0, 18.0, 10.0, 12.0),
        ),
        RecipeTemplate(
            t("Minestrone", "Minestrone"),
            t("Olasz zöldségleves tésztával — egy tál, egész ebéd.", "The Italian vegetable soup with pasta — one bowl, a whole lunch."),
            40,
            listOf(
                t("Dinszteld a sárgarépát, a zellert és a hagymát.", "Soften the carrot, the celery and the onion."),
                t("Öntsd fel alaplével, add hozzá a paradicsomot és a babot.", "Pour in the stock and add the tomatoes and the beans."),
                t("Főzd húsz percig, majd tedd bele a tésztát.", "Simmer for twenty minutes and then add the pasta."),
            ),
            listOf(
                ing("apró tészta", "small pasta", 70.0, "g", Aisle.SZARAZARU),
                ing("fehérbab konzerv", "tinned white beans", 200.0, "g", Aisle.SZARAZARU),
                ing("sárgarépa", "carrot", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("zeller", "celery", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("paradicsomkonzerv", "tinned tomatoes", 200.0, "g", Aisle.SZARAZARU),
                OIL, SALT_PEPPER,
            ),
            kcal(580.0, 24.0, 96.0, 12.0, 18.0, 14.0),
        ),
        RecipeTemplate(
            t("Mapo tofu rizzsel", "Mapo tofu with rice"),
            t("Szecsuáni klasszikus — csípős, fehérjedús, gyors.", "The Sichuan classic — hot, high in protein and quick."),
            25,
            listOf(
                t("Pirítsd a darált húst, add hozzá a csilipasztát.", "Fry the mince and add the chilli bean paste."),
                t("Öntsd fel alaplével, tedd bele a kockázott tofut.", "Add stock and slip in the diced tofu."),
                t("Sűrítsd keményítővel, szórd meg újhagymával, tálald rizzsel.", "Thicken it with cornflour, scatter over spring onion and serve with rice."),
            ),
            listOf(
                ing("darált marhahús", "beef mince", 120.0, "g", Aisle.HUS_HAL),
                ing("tofu", "firm tofu", 200.0, "g", Aisle.SZARAZARU),
                ing("rizs", "rice", 70.0, "g", Aisle.SZARAZARU),
                ing("csilipaszta", "chilli bean paste", 20.0, "g", Aisle.FUSZER),
                ing("újhagyma", "spring onion", 30.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                RAPESEED,
            ),
            kcal(650.0, 44.0, 66.0, 24.0, 6.0, 4.0),
        ),
        RecipeTemplate(
            t("Vietnami rizstésztasaláta csirkével", "Vietnamese rice noodle salad with chicken"),
            t("Bún gà: hideg tészta, meleg hús, friss zöldek.", "Bún gà: cold noodles, warm meat and fresh herbs."),
            25,
            listOf(
                t("Főzd meg a gluténmentes rizstésztát, öblítsd le hideg vízzel.", "Cook the rice noodles and rinse them cold."),
                t("Süsd meg a csirkét, szeleteld fel.", "Cook the chicken and slice it."),
                t("Keverj öntetet lime-ból, cukorból és csiliből.", "Whisk a dressing from lime, sugar and chilli."),
                t("Rakd össze salátával, uborkával, mentával.", "Build it with salad leaves, cucumber and mint."),
            ),
            listOf(
                ing("gluténmentes rizstészta", "rice noodles", 80.0, "g", Aisle.SZARAZARU),
                ing("csirkemell", "chicken breast", 170.0, "g", Aisle.HUS_HAL),
                ing("uborka", "cucumber", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("sárgarépa", "carrot", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("friss menta", "fresh mint", 10.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("lime", "lime", 1.0, "db", Aisle.ZOLDSEG_GYUMOLCS),
            ),
            kcal(590.0, 44.0, 80.0, 8.0, 6.0, 10.0),
        ),
        RecipeTemplate(
            t("Lengyel káposztás-pulykás egytálétel", "Polish cabbage and turkey one-pot"),
            t("Bigos szellemében, könnyítve. Gluténmentes.", "In the spirit of bigos, lightened. Gluten-free."),
            50,
            listOf(
                t("Pirítsd meg a darált pulykahúst.", "Brown the turkey mince."),
                t("Add hozzá a felaprított káposztát és a hagymát.", "Add the shredded cabbage and the onion."),
                t("Fűszerezd babérlevéllel és borókával, főzd negyven percig.", "Season it with bay and juniper and cook it for forty minutes."),
            ),
            listOf(
                ing("darált pulykahús", "turkey mince", 180.0, "g", Aisle.HUS_HAL),
                ing("fejes káposzta", "white cabbage", 300.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("burgonya", "potato", 200.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("vöröshagyma", "onion", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("babérlevél", "bay leaf", 2.0, "db", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(600.0, 46.0, 56.0, 20.0, 12.0, 14.0),
        ),
        RecipeTemplate(
            t("Ukrán borscs", "Ukrainian borscht"),
            t("Céklaleves, ami főételnek is elég.", "A beetroot soup substantial enough to be dinner."),
            60,
            listOf(
                t("Főzz húslevest a marhahúsból negyven percig.", "Simmer the beef into a broth for forty minutes."),
                t("Add hozzá a reszelt céklát, a káposztát és a burgonyát.", "Add the grated beetroot, the cabbage and the potato."),
                t("Főzd húsz percig, tálald tejföllel és kaporral.", "Cook it twenty minutes more and serve with soured cream and dill."),
            ),
            listOf(
                ing("marhahús", "stewing beef", 150.0, "g", Aisle.HUS_HAL),
                ing("cékla", "beetroot", 250.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("fejes káposzta", "white cabbage", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("burgonya", "potato", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("tejföl", "soured cream", 40.0, "g", Aisle.TEJTERMEK),
                ing("kapor", "dill", 10.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
            ),
            kcal(610.0, 42.0, 56.0, 22.0, 12.0, 22.0),
        ),
        RecipeTemplate(
            t("Sült padlizsán bulgurral", "Roast aubergine with bulgur"),
            t("Török mezze főételnek. Vegán.", "A Turkish mezze grown into a main. Vegan."),
            40,
            listOf(
                t("Vágd félbe a padlizsánt, irdald be, olajozd meg.", "Halve the aubergines, score them and oil them."),
                t("Süsd 200 fokon harminc percig.", "Roast them at 200°C for thirty minutes."),
                t("Főzz bulgurt, keverd bele a paradicsomot és a petrezselymet.", "Cook the bulgur and fold in the tomato and parsley."),
                t("Töltsd meg vele a padlizsánt.", "Fill the aubergines with it."),
            ),
            listOf(
                ing("padlizsán", "aubergine", 350.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("bulgur", "bulgur", 80.0, "g", Aisle.SZARAZARU),
                ing("paradicsom", "tomato", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("petrezselyem", "parsley", 15.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(560.0, 16.0, 88.0, 18.0, 18.0, 16.0),
        ),
        RecipeTemplate(
            t("Sült csirkecomb sütőtökkel", "Roast chicken thigh with squash"),
            t("Egy tepsi, két hozzávaló, semmi mosogatás.", "One tray, two ingredients and next to no washing up."),
            45,
            listOf(
                t("Forgasd a sütőtököt és a csirkét olajba és fűszerekbe.", "Toss the squash and the chicken in oil and spices."),
                t("Borítsd egy tepsibe, süsd 200 fokon negyven percig.", "Tip it all into one tray and roast at 200°C for forty minutes."),
                t("Félidőben forgasd meg.", "Turn it once halfway through."),
            ),
            listOf(
                ing("csirkecomb", "chicken thigh", 200.0, "g", Aisle.HUS_HAL),
                ing("sütőtök", "butternut squash", 300.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("pirospaprika", "sweet paprika", 3.0, "g", Aisle.FUSZER, staple = true),
                ing("rozmaring", "rosemary", 3.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(600.0, 40.0, 42.0, 30.0, 8.0, 16.0),
        ),
        RecipeTemplate(
            t("Tonhalas babsaláta", "Tuna and bean salad"),
            t("Toszkán konyha: öt perc, ha nyitva a konzerv.", "Tuscan cooking: five minutes once the tin is open."),
            10,
            listOf(
                t("Csepegtesd le a tonhalat és a babot.", "Drain the tuna and the beans."),
                t("Keverd össze lilahagymával és petrezselyemmel.", "Mix them with red onion and parsley."),
                t("Ízesítsd olívaolajjal és citrommal.", "Dress it with olive oil and lemon."),
            ),
            listOf(
                ing("tonhalkonzerv", "tinned tuna", 150.0, "g", Aisle.HUS_HAL),
                ing("fehérbab konzerv", "tinned white beans", 250.0, "g", Aisle.SZARAZARU),
                ing("lilahagyma", "red onion", 50.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("petrezselyem", "parsley", 15.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(580.0, 48.0, 52.0, 18.0, 16.0, 4.0),
        ),
        RecipeTemplate(
            t("Karfiolrizses csirke curry", "Chicken curry with cauliflower rice"),
            t("Alacsony szénhidráttartalmú változat, gluténmentes.", "A lower-carbohydrate take, and gluten-free."),
            30,
            listOf(
                t("Reszeld le a karfiolt rizs méretűre, pirítsd meg olajon.", "Grate the cauliflower to the size of rice and fry it."),
                t("Süsd a csirkét, add hozzá a curryport és a kókusztejet.", "Cook the chicken, then add the curry powder and the coconut milk."),
                t("Főzd tizenöt percig, tálald a karfiolrizzsel.", "Simmer for fifteen minutes and serve on the cauliflower rice."),
            ),
            listOf(
                ing("csirkemell", "chicken breast", 200.0, "g", Aisle.HUS_HAL),
                ing("karfiol", "cauliflower", 350.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("kókusztej", "coconut milk", 150.0, "ml", Aisle.SZARAZARU),
                ing("curry por", "curry powder", 8.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(560.0, 52.0, 24.0, 30.0, 10.0, 12.0),
        ),
        RecipeTemplate(
            t("Etióp lencsefőzelék rizzsel", "Ethiopian lentil stew with rice"),
            t("Misir wot: berbere fűszerkeverék, vörös lencse, semmi állati.", "Misir wot: berbere spice, red lentils and nothing from an animal."),
            35,
            listOf(
                t("Dinszteld a hagymát zsiradék nélkül, amíg le nem adja a levét.", "Sweat the onion dry until it gives up its liquid."),
                t("Add hozzá az olajat és a berbere fűszerkeveréket.", "Add the oil and the berbere."),
                t("Öntsd hozzá a lencsét és a vizet, főzd huszonöt percig.", "Tip in the lentils and water and simmer for twenty-five minutes."),
            ),
            listOf(
                ing("vörös lencse", "red lentils", 110.0, "g", Aisle.SZARAZARU),
                ing("vöröshagyma", "onion", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("berbere fűszerkeverék", "berbere spice mix", 8.0, "g", Aisle.FUSZER),
                ing("basmati rizs", "basmati rice", 70.0, "g", Aisle.SZARAZARU),
                RAPESEED, SALT_PEPPER,
            ),
            kcal(620.0, 28.0, 104.0, 12.0, 16.0, 8.0),
        ),
        RecipeTemplate(
            t("Halloumi saláta sült céklával", "Halloumi salad with roast beetroot"),
            t("Ciprusi sajt, ami nem olvad el — vegetáriánus, gluténmentes.", "The Cypriot cheese that will not melt — vegetarian and gluten-free."),
            35,
            listOf(
                t("Süsd a céklát fóliában negyven percig, vagy használj főttet.", "Roast the beetroot in foil for forty minutes, or use ready-cooked."),
                t("Süsd a halloumit száraz serpenyőben, oldalanként két percig.", "Fry the halloumi in a dry pan, two minutes a side."),
                t("Keverd össze a salátával, locsold meg olajjal és citrommal.", "Toss it with the leaves and dress it with oil and lemon."),
            ),
            listOf(
                ing("halloumi", "halloumi", 150.0, "g", Aisle.TEJTERMEK),
                ing("cékla", "beetroot", 250.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("vegyes saláta", "mixed salad leaves", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("tökmag", "pumpkin seeds", 20.0, "g", Aisle.SZARAZARU),
                OIL, SALT_PEPPER,
            ),
            kcal(590.0, 32.0, 32.0, 38.0, 9.0, 22.0),
        ),
        RecipeTemplate(
            t("Sült sertéskaraj almás párolt káposztával", "Roast pork loin with apple and cabbage"),
            t("Közép-európai páros, ahogy Bécstől Krakkóig eszik.", "A Central European pairing, from Vienna to Kraków."),
            45,
            listOf(
                t("Sózd, borsozd a karajt, süsd serpenyőben kérgesre.", "Season the loin and sear it in a pan."),
                t("Told sütőbe 180 fokra húsz percre.", "Finish it in the oven at 180°C for twenty minutes."),
                t("Párold a káposztát az almával és a köménymaggal.", "Braise the cabbage with the apple and the caraway."),
            ),
            listOf(
                ing("sertéskaraj", "pork loin", 180.0, "g", Aisle.HUS_HAL),
                ing("fejes káposzta", "white cabbage", 300.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("alma", "apple", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("burgonya", "potato", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("köménymag", "caraway seeds", 3.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(640.0, 44.0, 58.0, 24.0, 12.0, 24.0),
        ),
        RecipeTemplate(
            t("Japán levesestál tofuval és zöldséggel", "Japanese broth bowl with tofu and greens"),
            t("Miso alapú leves, tíz perc alatt.", "A miso-based bowl in ten minutes."),
            15,
            listOf(
                t("Melegíts vizet, de ne forrald fel.", "Heat the water but do not let it boil."),
                t("Keverd el benne a miszópasztát.", "Whisk the miso paste into it."),
                t("Tedd bele a kockázott tofut, a wakame algát és az újhagymát.", "Add the diced tofu, the wakame and the spring onion."),
                t("Tálald mellé a főtt rizst.", "Serve the rice alongside."),
            ),
            listOf(
                ing("tofu", "silken tofu", 200.0, "g", Aisle.SZARAZARU),
                ing("miszópaszta", "miso paste", 30.0, "g", Aisle.FUSZER),
                ing("wakame alga", "wakame seaweed", 5.0, "g", Aisle.SZARAZARU),
                ing("rizs", "rice", 80.0, "g", Aisle.SZARAZARU),
                ing("újhagyma", "spring onion", 30.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
            ),
            kcal(560.0, 30.0, 82.0, 12.0, 6.0, 4.0),
        ),
        RecipeTemplate(
            t("Bárányos zöldségragu kölessel", "Lamb and vegetable stew with millet"),
            t("Kaukázusi ihletésű, gluténmentes egytálétel.", "A Caucasus-leaning, gluten-free one-pot."),
            70,
            listOf(
                t("Pirítsd le a bárányhúst minden oldalán.", "Brown the lamb on every side."),
                t("Add hozzá a sárgarépát, a paprikát és a paradicsomot.", "Add the carrot, the pepper and the tomato."),
                t("Főzd lassú tűzön egy órán át.", "Simmer it for an hour."),
                t("Főzz kölest mellé.", "Cook the millet to go with it."),
            ),
            listOf(
                ing("báránylapocka", "lamb shoulder", 170.0, "g", Aisle.HUS_HAL),
                ing("köles", "millet", 70.0, "g", Aisle.SZARAZARU),
                ing("sárgarépa", "carrot", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("kaliforniai paprika", "bell pepper", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("paradicsom", "tomato", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(650.0, 42.0, 64.0, 26.0, 10.0, 12.0),
        ),
        RecipeTemplate(
            t("Sült garnéla rizstésztával", "Seared prawns with rice noodles"),
            t("Délkelet-ázsiai gyors vacsora, gluténmentesen.", "A South-East Asian quick dinner, gluten-free."),
            20,
            listOf(
                t("Áztasd be a gluténmentes rizstésztát forró vízbe.", "Soak the rice noodles in hot water."),
                t("Süsd a garnélát fokhagymás olajon két percig oldalanként.", "Sear the prawns in garlic oil, two minutes a side."),
                t("Forgasd össze a tésztával, a zöldségekkel és a lime-mal.", "Toss everything together with the noodles, the vegetables and the lime."),
            ),
            listOf(
                ing("garnélarák", "prawns", 200.0, "g", Aisle.HUS_HAL),
                ing("gluténmentes rizstészta", "rice noodles", 80.0, "g", Aisle.SZARAZARU),
                ing("wok zöldségkeverék", "stir-fry vegetable mix", 200.0, "g", Aisle.FAGYASZTOTT),
                ing("fokhagyma", "garlic", 2.0, "gerezd", Aisle.ZOLDSEG_GYUMOLCS),
                ing("lime", "lime", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                RAPESEED,
            ),
            kcal(580.0, 42.0, 80.0, 10.0, 6.0, 6.0),
        ),
        RecipeTemplate(
            t("Kukoricadarás zöldségragu", "Polenta with vegetable ragù"),
            t("Észak-olasz polenta, vegán és gluténmentes.", "Northern Italian polenta, vegan and gluten-free."),
            35,
            listOf(
                t("Főzd a kukoricadarát sós vízben, folyamatosan keverve.", "Cook the polenta in salted water, stirring all the time."),
                t("Pirítsd a gombát és a cukkinit olajon.", "Fry the mushrooms and the courgette in oil."),
                t("Add hozzá a paradicsomot, főzd tizenöt percig.", "Add the tomatoes and simmer for fifteen minutes."),
            ),
            listOf(
                ing("kukoricadara", "polenta", 90.0, "g", Aisle.SZARAZARU),
                ing("gomba", "mushrooms", 200.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("cukkini", "courgette", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("paradicsomkonzerv", "tinned tomatoes", 200.0, "g", Aisle.SZARAZARU),
                OIL, SALT_PEPPER,
            ),
            kcal(560.0, 16.0, 92.0, 14.0, 10.0, 12.0),
        ),
        RecipeTemplate(
            t("Töltött cukkini darált pulykával", "Stuffed courgette with turkey mince"),
            t("Mediterrán, gluténmentes, alacsony szénhidráttartalmú.", "Mediterranean, gluten-free and low in carbohydrate."),
            45,
            listOf(
                t("Vájd ki a félbevágott cukkiniket.", "Hollow out the halved courgettes."),
                t("Pirítsd meg a darált húst a kivájt hússal és a fűszerekkel.", "Fry the mince with the scooped-out flesh and the herbs."),
                t("Töltsd vissza, süsd 190 fokon huszonöt percig.", "Fill them back up and bake at 190°C for twenty-five minutes."),
            ),
            listOf(
                ing("darált pulykahús", "turkey mince", 200.0, "g", Aisle.HUS_HAL),
                ing("cukkini", "courgette", 400.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("paradicsom", "tomato", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("oregánó", "oregano", 2.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(520.0, 48.0, 24.0, 26.0, 8.0, 16.0),
        ),
    )

    // =============================================================================
    // NASSOLNIVALÓ (tízórai, uzsonna, esti falat)
    // =============================================================================

    val SNACKS: List<RecipeTemplate> = listOf(
        RecipeTemplate(
            t("Görög joghurt dióval", "Greek yoghurt with walnuts"),
            t("Gyors fehérjeforrás két étkezés között.", "A quick protein hit between meals."),
            2,
            listOf(t("Keverd össze.", "Stir them together.")),
            listOf(
                ing("görög joghurt", "Greek yoghurt", 150.0, "g", Aisle.TEJTERMEK),
                ing("dió", "walnuts", 15.0, "g", Aisle.SZARAZARU),
            ),
            kcal(220.0, 16.0, 9.0, 13.0, 1.0, 7.0),
        ),
        RecipeTemplate(
            t("Alma mogyoróvajjal", "Apple with peanut butter"),
            t("Rost és jó zsírok.", "Fibre and good fats."),
            2,
            listOf(t("Szeleteld fel az almát, és kend meg a szeleteket.", "Slice the apple and spread the slices.")),
            listOf(
                ing("alma", "apple", 1.0, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("mogyoróvaj", "peanut butter", 20.0, "g", Aisle.SZARAZARU),
            ),
            kcal(220.0, 6.0, 25.0, 11.0, 5.0, 18.0),
        ),
        RecipeTemplate(
            t("Sárgarépa hummusszal", "Carrot sticks with hummus"),
            t("Ropogós, alacsony kalóriájú nassolnivaló.", "A crunchy, low-calorie snack."),
            3,
            listOf(
                t("Vágd a répát csíkokra.", "Cut the carrots into sticks."),
                t("Mártsd a hummuszba.", "Dip them in the hummus."),
            ),
            listOf(
                ing("sárgarépa", "carrot", 150.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("hummusz", "hummus", 60.0, "g", Aisle.SZARAZARU),
            ),
            kcal(210.0, 7.0, 24.0, 9.0, 7.0, 8.0),
        ),
        RecipeTemplate(
            t("Guacamole zöldségcsíkokkal", "Guacamole with vegetable sticks"),
            t("Mexikói mártogatós: vegán, gluténmentes, diómentes.", "The Mexican dip: vegan, gluten-free and nut-free."),
            8,
            listOf(
                t("Törd össze az avokádót egy villával.", "Crush the avocado with a fork."),
                t("Keverd bele a lime levét, a sót és az apróra vágott paradicsomot.", "Stir in the lime juice, the salt and the chopped tomato."),
                t("Vágj mellé uborkát és paprikát csíkokra.", "Cut cucumber and pepper into sticks to go with it."),
            ),
            listOf(
                ing("avokádó", "avocado", 1.0, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("paradicsom", "tomato", 80.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("uborka", "cucumber", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("lime", "lime", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                SALT_PEPPER,
            ),
            kcal(260.0, 5.0, 18.0, 20.0, 11.0, 6.0),
        ),
        RecipeTemplate(
            t("Pörkölt csicseriborsó", "Roasted chickpeas"),
            t("Közel-keleti ropogtatnivaló sós nasi helyett.", "A Middle Eastern crunch instead of crisps."),
            35,
            listOf(
                t("Csepegtesd le és szárítsd meg a csicseriborsót.", "Drain the chickpeas and dry them."),
                t("Forgasd olajba, füstölt paprikába és sóba.", "Toss them in oil, smoked paprika and salt."),
                t("Süsd 200 fokon harminc percig, félúton rázd meg.", "Roast at 200°C for thirty minutes, shaking the tray halfway."),
            ),
            listOf(
                ing("csicseriborsó konzerv", "tinned chickpeas", 200.0, "g", Aisle.SZARAZARU),
                ing("füstölt pirospaprika", "smoked paprika", 3.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(250.0, 12.0, 32.0, 8.0, 10.0, 2.0),
        ),
        RecipeTemplate(
            t("Edamame tengeri sóval", "Edamame with sea salt"),
            t("Japán szójabab hüvelyben — sok fehérje, kevés dolog vele.", "Japanese soybeans in the pod — lots of protein, little work."),
            8,
            listOf(
                t("Főzd a fagyasztott edamamét sós vízben öt percig.", "Boil the frozen edamame in salted water for five minutes."),
                t("Szórd meg tengeri sóval, a hüvelyből edd ki.", "Sprinkle with sea salt and eat them from the pod."),
            ),
            listOf(
                ing("edamame", "edamame", 200.0, "g", Aisle.FAGYASZTOTT),
                ing("tengeri só", "sea salt", 2.0, "g", Aisle.FUSZER, staple = true),
            ),
            kcal(230.0, 22.0, 18.0, 9.0, 10.0, 4.0),
        ),
        RecipeTemplate(
            t("Főtt tojás sóval és paprikával", "Boiled eggs with salt and paprika"),
            t("A legegyszerűbb fehérje: gluténmentes, tejtermék nélkül.", "The simplest protein there is: gluten-free and dairy-free."),
            12,
            listOf(
                t("Tedd a tojásokat forrásban lévő vízbe kilenc percre.", "Lower the eggs into boiling water for nine minutes."),
                t("Hűtsd le hideg vízben, hámozd meg.", "Cool them in cold water and peel them."),
                t("Szórd meg sóval és pirospaprikával.", "Dust them with salt and paprika."),
            ),
            listOf(
                ing("tojás", "eggs", 2.0, "db", Aisle.TEJTERMEK),
                ing("pirospaprika", "sweet paprika", 1.0, "g", Aisle.FUSZER, staple = true),
                SALT_PEPPER,
            ),
            kcal(160.0, 13.0, 1.0, 11.0, 0.0, 1.0),
        ),
        RecipeTemplate(
            t("Tzatziki uborkacsíkokkal", "Tzatziki with cucumber sticks"),
            t("Görög mártogatós, hűsítő és fehérjés.", "The Greek dip: cooling and full of protein."),
            10,
            listOf(
                t("Reszeld le az uborka felét, nyomkodd ki a levét.", "Grate half the cucumber and squeeze the water out."),
                t("Keverd a joghurtba fokhagymával, kaporral és olajjal.", "Stir it into the yoghurt with garlic, dill and oil."),
                t("A maradék uborkát vágd csíkokra mártogatáshoz.", "Cut the rest of the cucumber into sticks for dipping."),
            ),
            listOf(
                ing("görög joghurt", "Greek yoghurt", 180.0, "g", Aisle.TEJTERMEK),
                ing("uborka", "cucumber", 200.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("fokhagyma", "garlic", 1.0, "gerezd", Aisle.ZOLDSEG_GYUMOLCS),
                ing("kapor", "dill", 5.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                OIL,
            ),
            kcal(220.0, 18.0, 14.0, 11.0, 2.0, 10.0),
        ),
        RecipeTemplate(
            t("Banán étcsokoládéval", "Banana with dark chocolate"),
            t("Édesség, ami nem csak cukor.", "Something sweet that is not only sugar."),
            3,
            listOf(
                t("Szeleteld fel a banánt.", "Slice the banana."),
                t("Reszeld rá az étcsokoládét.", "Grate the dark chocolate over it."),
            ),
            listOf(
                ing("banán", "banana", 1.0, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("étcsokoládé", "dark chocolate", 20.0, "g", Aisle.SZARAZARU),
            ),
            kcal(220.0, 3.0, 34.0, 9.0, 5.0, 24.0),
        ),
        RecipeTemplate(
            t("Pirított tökmag", "Toasted pumpkin seeds"),
            t("Diómentes ropogtatnivaló mindenkinek.", "A nut-free crunch that suits everyone."),
            10,
            listOf(
                t("Pirítsd a tökmagot száraz serpenyőben öt percig.", "Toast the seeds in a dry pan for five minutes."),
                t("Rázogasd, hogy ne égjen meg, majd sózd meg.", "Shake the pan so they do not burn, then salt them."),
            ),
            listOf(
                ing("tökmag", "pumpkin seeds", 40.0, "g", Aisle.SZARAZARU),
                ing("tengeri só", "sea salt", 1.0, "g", Aisle.FUSZER, staple = true),
            ),
            kcal(230.0, 12.0, 6.0, 19.0, 3.0, 1.0),
        ),
        RecipeTemplate(
            t("Datolya tahinivel", "Dates with tahini"),
            t("Levantei édesség két hozzávalóból.", "A Levantine sweet from two ingredients."),
            3,
            listOf(
                t("Vágd fel a datolyát, vedd ki a magját.", "Split the dates and take the stones out."),
                t("Tölts bele tahinit.", "Spoon tahini into them."),
            ),
            listOf(
                ing("datolya", "dates", 50.0, "g", Aisle.SZARAZARU),
                ing("tahini", "tahini", 20.0, "g", Aisle.SZARAZARU),
            ),
            kcal(250.0, 5.0, 36.0, 11.0, 5.0, 30.0),
        ),
        RecipeTemplate(
            t("Körözött zöldségcsíkokkal", "Hungarian körözött with vegetable sticks"),
            t("Magyar túrókrém pirospaprikával.", "The Hungarian paprika curd spread."),
            8,
            listOf(
                t("Törd össze a túrót, keverd bele a pirospaprikát és a köményt.", "Mash the curd and stir in the paprika and the caraway."),
                t("Keverj bele apróra vágott lilahagymát.", "Fold in the finely chopped red onion."),
                t("Tálald paprikacsíkokkal.", "Serve it with strips of pepper."),
            ),
            listOf(
                ing("túró", "curd cheese", 150.0, "g", Aisle.TEJTERMEK),
                ing("kaliforniai paprika", "bell pepper", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("lilahagyma", "red onion", 30.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("pirospaprika", "sweet paprika", 3.0, "g", Aisle.FUSZER, staple = true),
            ),
            kcal(230.0, 24.0, 14.0, 9.0, 3.0, 8.0),
        ),
        RecipeTemplate(
            t("Sült édesburgonya-szeletek", "Sweet potato wedges"),
            t("Vegán, gluténmentes, diómentes és szójamentes.", "Vegan, gluten-free, nut-free and soy-free."),
            30,
            listOf(
                t("Vágd az édesburgonyát cikkekre.", "Cut the sweet potato into wedges."),
                t("Forgasd olajba és füstölt paprikába.", "Toss them in oil and smoked paprika."),
                t("Süsd 200 fokon huszonöt percig.", "Roast at 200°C for twenty-five minutes."),
            ),
            listOf(
                ing("édesburgonya", "sweet potato", 200.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("füstölt pirospaprika", "smoked paprika", 2.0, "g", Aisle.FUSZER, staple = true),
                OIL, SALT_PEPPER,
            ),
            kcal(230.0, 3.0, 40.0, 7.0, 6.0, 12.0),
        ),
        RecipeTemplate(
            t("Kókuszos-mangós tál", "Coconut and mango bowl"),
            t("Trópusi édesség tejtermék nélkül.", "A tropical sweet without any dairy."),
            5,
            listOf(
                t("Kanalazd tálba a növényi kókuszjoghurtot.", "Spoon the plant-based coconut yoghurt into a bowl."),
                t("Tedd rá a kockázott mangót és a kókuszreszeléket.", "Add the diced mango and the desiccated coconut."),
            ),
            listOf(
                ing("növényi kókuszjoghurt", "plant-based coconut yoghurt", 150.0, "g", Aisle.TEJTERMEK),
                ing("mangó", "mango", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("kókuszreszelék", "desiccated coconut", 15.0, "g", Aisle.SZARAZARU),
            ),
            kcal(250.0, 3.0, 30.0, 14.0, 5.0, 24.0),
        ),
        RecipeTemplate(
            t("Rizslapok avokádókrémmel", "Rice cakes with avocado"),
            t("Gluténmentes, tejtermék nélküli, három perc.", "Gluten-free, dairy-free, three minutes."),
            3,
            listOf(
                t("Törd össze az avokádót sóval és citrommal.", "Crush the avocado with salt and lemon."),
                t("Kend a rizslapokra, szórd meg csilipehellyel.", "Spread it on the rice cakes and scatter over the chilli flakes."),
            ),
            listOf(
                ing("puffasztott rizslap", "rice cakes", 30.0, "g", Aisle.SZARAZARU),
                ing("avokádó", "avocado", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                ing("citrom", "lemon", 0.25, "db", Aisle.ZOLDSEG_GYUMOLCS),
                SALT_PEPPER,
            ),
            kcal(230.0, 4.0, 28.0, 12.0, 6.0, 1.0),
        ),
        RecipeTemplate(
            t("Sós mandula és bogyós gyümölcs", "Salted almonds with berries"),
            t("Zsír és rost egy marékban.", "Fat and fibre in one handful."),
            2,
            listOf(t("Mérd ki a mandulát, edd mellé a gyümölcsöt.", "Weigh out the almonds and eat the fruit with them.")),
            listOf(
                ing("mandula", "almonds", 30.0, "g", Aisle.SZARAZARU),
                ing("málna", "raspberries", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
            ),
            kcal(250.0, 9.0, 16.0, 17.0, 10.0, 8.0),
        ),
        RecipeTemplate(
            t("Cottage cheese paradicsommal", "Cottage cheese with tomato"),
            t("Sok fehérje, kevés kalória, semmi készülés.", "Lots of protein, few calories, no cooking."),
            3,
            listOf(
                t("Kanalazd tálba a cottage cheese-t.", "Spoon the cottage cheese into a bowl."),
                t("Tedd rá a paradicsomot, borsozd meg.", "Add the tomato and grind pepper over it."),
            ),
            listOf(
                ing("cottage cheese", "cottage cheese", 200.0, "g", Aisle.TEJTERMEK),
                ing("koktélparadicsom", "cherry tomatoes", 120.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                SALT_PEPPER,
            ),
            kcal(200.0, 24.0, 12.0, 6.0, 2.0, 8.0),
        ),
        RecipeTemplate(
            t("Zöldborsókrém zöldségcsíkokkal", "Pea dip with vegetable sticks"),
            t("Vegán, gluténmentes, diómentes és szezámmentes mártogatós.", "A vegan dip that is free of gluten, nuts and sesame."),
            10,
            listOf(
                t("Főzd meg a fagyasztott zöldborsót három percig.", "Boil the frozen peas for three minutes."),
                t("Turmixold össze mentával, citrommal és olajjal.", "Blend them with mint, lemon and oil."),
                t("Tálald sárgarépa- és uborkacsíkokkal.", "Serve with carrot and cucumber sticks."),
            ),
            listOf(
                ing("zöldborsó", "garden peas", 150.0, "g", Aisle.FAGYASZTOTT),
                ing("sárgarépa", "carrot", 100.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("friss menta", "fresh mint", 5.0, "g", Aisle.ZOLDSEG_GYUMOLCS),
                ing("citrom", "lemon", 0.5, "db", Aisle.ZOLDSEG_GYUMOLCS),
                OIL, SALT_PEPPER,
            ),
            kcal(220.0, 10.0, 26.0, 9.0, 11.0, 8.0),
        ),
    )
}

/**
 * Kétnyelvű hozzávaló. A mennyiség és a polc nyelvfüggetlen.
 *
 * A polcot [Aisle]-ként vesszük át, de `String`-ként tároljuk, mert az [AiIngredient]
 * is azt vár — így viszont elgépelni nem lehet.
 */
data class RecipeIngredient(
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

/** Egy sablonfogás, mindkét nyelven. */
data class RecipeTemplate(
    val name: Text,
    val description: Text,
    val prepMinutes: Int,
    val steps: List<Text>,
    val ingredients: List<RecipeIngredient>,
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
