package hu.mealpilot.core.model

import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.core.i18n.Localized

/**
 * Étrendi kizárások: allergiák, intoleranciák és tudatos döntések.
 *
 * Minden tétel hoz magával egy magyar kulcsszólistát is. Ez azért kell, mert az AI-ra
 * egy allergiát nem szabad rábízni: a legenerált étrend hozzávalóit gépileg is átnézzük
 * ([hu.mealpilot.core.ai.RestrictionChecker]), és találat esetén a terv nem jut el a
 * felhasználóig, hanem újratervezést kér.
 */
enum class DietRestriction(
    override val hu: String,
    val group: Group,
    val severity: Severity,
    /** Rövid magyarázat a felületre. */
    val note: String = "",
    /** Az AI-nak adott kizárási szabály. */
    val rule: String,
    /** Magyar hozzávaló-kulcsszavak a gépi ellenőrzéshez. */
    val keywords: List<String>,
    /**
     * Ha a hozzávaló neve ezek valamelyikét tartalmazza, a találat nem valódi
     * (pl. „gluténmentes tészta”). Kazeinallergiánál szándékosan üres: a laktózmentes
     * tej is tartalmaz tejfehérjét.
     */
    val safeMarkers: List<String> = emptyList(),
    /**
     * Szavak, amiket a kulcsszóegyezés ellenére NEM tekintünk találatnak.
     * A magyar szóelejű egyezés miatt kellenek: a „bor" különben eltalálná a borsót,
     * a borsot és a borjút is, a „bab" pedig a babérlevelet.
     */
    val exceptions: List<String> = emptyList(),

    // ---------- Angol változat ----------
    // Nem fordítás, hanem külön adat: az angol kulcsszavak nélkül az allergiaszűrés egy
    // angol étrenden némán nem találna semmit. Ez a legrosszabb hibafajta, amit ez az
    // app el tud követni, ezért minden tételnek KÖTELEZŐ angol listát hoznia.
    override val en: String,
    val noteEn: String = "",
    val ruleEn: String,
    val keywordsEn: List<String>,
    val safeMarkersEn: List<String> = emptyList(),
    val exceptionsEn: List<String> = emptyList(),
) : Localized {


    // ---------- Gabona, tej ----------
    GLUTEN(
        "Glutén", Group.CEREAL_DAIRY, Severity.STRICT,
        "Búza, rozs, árpa. Cölikánál a keresztszennyezés is számít.",
        "Semmilyen gluténtartalmú gabona (búza, rozs, árpa, tönköly), liszt, tészta, kenyér, " +
            "zsemlemorzsa, bulgur, kuszkusz, szeitán. Zab csak igazoltan gluténmentes lehet.",
        listOf(
            "búza", "buza", "liszt", "tészta", "teszta", "kenyér", "kenyer", "zsemle", "kifli",
            "bulgur", "kuszkusz", "árpa", "arpa", "rozs", "tönköly", "tonkoly", "szeitán", "szeitan",
            "morzsa", "gríz", "griz", "tarhonya", "keksz", "ostya", "pékáru", "pekaru", "sör",
            "durum", "búzadara", "buzadara", "palacsinta", "piskóta", "piskota",
            "spagetti", "makaróni", "makaroni", "csusza", "galuska", "nokedli",
            // Ezek az ANGOL listán rajta voltak, a magyarról lemaradtak — pedig a
            // croissant és a bagett magyar reggeliben is gyakori.
            "croissant", "bagett", "briós", "brios", "muffin", "pogácsa", "pogacsa",
            "pirítós", "piritos", "panír", "panir", "panírozott", "panirozott",
            // Szóközzel: a „rántotta" is ezzel kezdődik, azt viszont a tojás kizárása
            // fogja meg, nem a gluténé.
            "rántott ", "rantott ",
        ),
        safeMarkers = listOf("gluténmentes", "glutenmentes", "gluténmentesen", "gm "),
        en = "Gluten",
        noteEn = "Wheat, rye, barley. With coeliac disease cross-contamination counts too.",
        ruleEn = "No gluten-containing grain (wheat, rye, barley, spelt), flour, pasta, bread, breadcrumbs, bulgur, couscous or seitan. Oats only if certified gluten-free.",
        keywordsEn = listOf(
            "wheat", "flour", "pasta", "spaghetti", "noodle", "bread", "bun", "roll", "baguette",
            "croissant", "bulgur", "couscous", "barley", "rye", "spelt", "seitan", "breadcrumb",
            "crumb", "semolina", "farro", "cracker", "biscuit", "cookie", "cake", "pastry", "pie",
            "pancake", "waffle", "beer", "durum", "malt", "tortilla", "wrap", "pita", "bagel",
            "muffin", "brioche", "orzo", "gnocchi", "dumpling"
        ),
        safeMarkersEn = listOf(
            "gluten-free", "gluten free", "glutenfree", "certified gluten",
            // A hajdina és a rizs gluténmentes, de a „flour" kulcsszó egyébként
            // elkapná a „buckwheat flour"-t és fölösleges újratervezést indítana.
            "buckwheat", "rice flour", "almond flour", "corn flour", "chickpea flour"
        ),
        exceptionsEn = listOf(
            "buckwheat", "gluten-free", "rice noodle", "glass noodle"
        ),
    ),
    LACTOSE(
        "Laktóz", Group.CEREAL_DAIRY, Severity.STRICT,
        "A laktózmentes tejtermék engedélyezett marad.",
        "Laktóztartalmú tejtermék nem szerepelhet; laktózmentes változat elfogadható.",
        listOf(
            "tej", "tejföl", "tejfol", "tejszín", "tejszin", "joghurt", "sajt", "vaj", "túró", "turo",
            "kefir", "író", "mascarpone", "ricotta", "feta", "mozzarella", "parmezán", "parmezan",
            "camembert", "cottage", "tejpor", "trappista", "gouda", "cheddar", "brie", "eidami", "körözött", "korozott",
        ),
        safeMarkers = listOf("laktózmentes", "laktozmentes", "növényi", "novenyi", "zabtej", "szójatej", "szojatej", "mandulatej", "rizstej"),
        // A vajbab és a vajretek zöldség — a „vaj" előtag ragadt beléjük.
        exceptions = listOf("vajbab", "vajretek"),
        en = "Lactose",
        noteEn = "Lactose-free dairy stays allowed.",
        ruleEn = "No lactose-containing dairy; lactose-free versions are fine.",
        keywordsEn = listOf(
            // A "whey" azért van külön, mert magyarul a „tejsavó" a „tej" előtaggal
            // fennakad, angolul viszont a "milk" nem előtagja a "whey"-nek — a magyar
            // összetett szó elöl hordozza a lényeget, az angol hátul. A tejsavófehérje
            // (koncentrátum) valódi laktózforrás, tehát ez nem elméleti rés.
            "milk", "cream", "yoghurt", "yogurt", "cheese", "butter", "curd", "kefir", "buttermilk",
            "whey", "mascarpone", "ricotta", "feta", "mozzarella", "parmesan", "camembert", "cheddar",
            "brie", "cottage", "custard", "creme fraiche", "quark", "ghee", "ice cream"
        ),
        safeMarkersEn = listOf(
            "lactose-free", "lactose free", "lactosefree", "oat milk", "soy milk", "almond milk",
            "rice milk", "coconut milk", "plant-based", "plant based", "oatmilk", "soymilk",
            "almondmilk"
        ),
        exceptionsEn = listOf(
            "milk thistle", "coconut milk"
        ),
    ),
    MILK_PROTEIN(
        "Tejfehérje (kazein)", Group.CEREAL_DAIRY, Severity.STRICT,
        "Ez szigorúbb a laktózérzékenységnél: a laktózmentes tej is tiltott.",
        "Semmilyen tejeredetű alapanyag, a laktózmentes változatok sem (azok is tartalmaznak " +
            "kazeint és tejsavófehérjét). Növényi alternatíva használható.",
        listOf(
            "tej", "tejföl", "tejfol", "tejszín", "tejszin", "joghurt", "sajt", "vaj", "túró", "turo",
            "kefir", "író", "mascarpone", "ricotta", "feta", "mozzarella", "parmezán", "parmezan",
            "camembert", "cottage", "tejpor", "kazein", "tejsavó", "tejsavo", "trappista", "gouda", "cheddar", "brie", "eidami", "körözött", "korozott",
        ),
        safeMarkers = listOf("növényi", "novenyi", "zabtej", "szójatej", "szojatej", "mandulatej", "rizstej", "kókusztej", "kokusztej"),
        // A vajbab és a vajretek zöldség — a „vaj" előtag ragadt beléjük.
        exceptions = listOf("vajbab", "vajretek"),
        en = "Milk protein (casein)",
        noteEn = "Stricter than lactose intolerance: lactose-free milk is out too.",
        ruleEn = "No dairy-derived ingredient at all, not even lactose-free ones (they still contain casein and whey). Plant alternatives are fine.",
        keywordsEn = listOf(
            "milk", "cream", "yoghurt", "yogurt", "cheese", "butter", "curd", "kefir", "buttermilk",
            "mascarpone", "ricotta", "feta", "mozzarella", "parmesan", "camembert", "cheddar", "brie",
            "cottage", "casein", "whey", "custard", "quark", "ghee", "ice cream"
        ),
        safeMarkersEn = listOf(
            "oat milk", "soy milk", "almond milk", "rice milk", "coconut milk", "plant-based",
            "plant based", "oatmilk", "soymilk", "almondmilk"
        ),
        exceptionsEn = listOf(
            "milk thistle", "coconut milk"
        ),
    ),

    // ---------- Állati eredetű ----------
    EGG(
        "Tojás", Group.ANIMAL, Severity.STRICT, "",
        "Tojás és tojástartalmú termék (majonéz, tojásos tészta) nem szerepelhet.",
        listOf(
            "tojás", "tojas", "majonéz", "majonez", "tojásfehérje", "tojassárgája",
            "rántotta", "rantotta", "omlett", "habcsók", "habcsok", "piskóta", "piskota",
            "tiramisu", "aioli", "madártej", "madartej",
        ),
        en = "Egg",
        noteEn = "",
        ruleEn = "No eggs or egg-containing products (mayonnaise, egg pasta).",
        keywordsEn = listOf(
            "egg", "mayonnaise", "mayo", "omelette", "omelet", "meringue", "frittata", "aioli"
        ),
        exceptionsEn = listOf(
            "eggplant"
        ),
    ),
    FISH(
        "Hal", Group.ANIMAL, Severity.STRICT, "",
        "Semmilyen hal, halkészítmény, halszósz vagy halolaj.",
        listOf(
            "hal", "lazac", "tonhal", "pisztráng", "pisztrang", "hering", "szardínia", "szardinia",
            "tőkehal", "tokehal", "harcsa", "ponty", "szardella", "makréla", "makrela", "halszósz", "halszosz",
        ),
        // A „hal" előtag beleragad a halloumiba (sajt), a halványítóba (halványító
        // zeller) és a halvába (szezámos édesség). Egyik sem hal.
        exceptions = listOf("halloumi", "halvány", "halvany", "halva"),
        en = "Fish",
        noteEn = "",
        ruleEn = "No fish, fish products, fish sauce or fish oil.",
        keywordsEn = listOf(
            "fish", "salmon", "tuna", "trout", "herring", "sardine", "cod", "mackerel", "anchovy",
            "anchovies", "haddock", "pollock", "bass", "bream", "carp", "catfish", "tilapia",
            "halibut", "fish sauce"
        ),
    ),
    CRUSTACEAN(
        "Rákfélék", Group.ANIMAL, Severity.STRICT, "",
        "Rák, garnéla, homár és minden rákféle tiltott.",
        // A „rak" (ékezet nélküli rák) ELŐTAGKÉNT a magyar egyik legtermékenyebb
        // szótöve: rakott krumpli, rakott kel, rakéta saláta, raktár. Emiatt minden
        // rakott étel rákallergiás találatnak számított, és fölösleges javító körökbe
        // vitte a tervet — ami két kör után minőségi hibaként el is bukik.
        //
        // Az ékezetes „rák" ártalmatlan: a „rakott" nem kezdődik vele. A modell
        // magyarul ékezetesen ír, tehát a veszteség elméleti, a nyereség nem.
        listOf(
            "rák", "garnéla", "garnela", "homár", "homar", "languszta", "scampi",
            // A „rák" három betű, ezért a szóVÉGI egyezés (ami négytől fut) nem
            // találja meg az összetételekben. A küszöb általános leszállítása viszont
            // rosszabb lenne: a „vaj" szóvégi egyezése a mogyoróvajat tenné tejtermékké.
            "tarisznyarák", "remeterák",
        ),
        // A Rákóczi túrós nem tengeri herkentyű.
        exceptions = listOf("rákóczi", "rakoczi"),
        en = "Crustaceans",
        noteEn = "",
        ruleEn = "No crab, prawn, shrimp, lobster or any crustacean.",
        keywordsEn = listOf(
            "crab", "prawn", "shrimp", "lobster", "crayfish", "langoustine", "scampi", "krill"
        ),
    ),
    MOLLUSC(
        "Puhatestűek", Group.ANIMAL, Severity.STRICT, "",
        "Kagyló, tintahal, polip, csiga nem szerepelhet.",
        listOf("kagyló", "kagylo", "tintahal", "polip", "csiga", "osztriga", "kalamári", "kalamari"),
        // A csigatészta a húsleves tartozéka, nem csiga.
        exceptions = listOf("csigatészt", "csigateszt"),
        en = "Molluscs",
        noteEn = "",
        ruleEn = "No mussels, squid, octopus or snails.",
        keywordsEn = listOf(
            "mussel", "clam", "squid", "calamari", "octopus", "snail", "oyster", "scallop", "cockle",
            "whelk"
        ),
    ),

    // ---------- Magvak, hüvelyesek ----------
    PEANUT(
        "Földimogyoró", Group.NUTS_SEEDS, Severity.STRICT, "",
        "Földimogyoró, mogyoróvaj, arachisolaj nem szerepelhet. A magyar „mogyoró” a boltban " +
            "rendszerint földimogyoró, ezért azt is kizárjuk — ha dióféle mogyorót írnál, " +
            "nevezd törökmogyorónak.",
        // A „mogyoró" a magyar köznyelvben földimogyorót is jelent: a „sós mogyoró"
        // és a „pörkölt mogyoró" is az. Enélkül a mogyoróallergiás felhasználó úgy
        // kapta volna meg a legveszélyesebb allergént, hogy semmi nem szól — a
        // dióféléket jelölő TREE_NUT ugyanis csak akkor fut, ha azt IS bejelölte.
        //
        // Cserébe a valódi mogyoró (dióféle) is földimogyoró-találatot ad. Ez az
        // elfogadható irány: egy fölösleges javító kör ára egy kör, egy kimaradt
        // földimogyoróé egy anafilaxia.
        listOf(
            "földimogyoró", "foldimogyoro", "mogyoróvaj", "mogyorovaj", "arachis",
            "mogyoró", "mogyoro",
        ),
        // A mogyoróhagyma (salotta) nem mogyoró, csak így hívják.
        exceptions = listOf("mogyoróhagym", "mogyorohagym"),
        en = "Peanut",
        noteEn = "",
        ruleEn = "No peanuts, peanut butter or arachis oil.",
        keywordsEn = listOf(
            "peanut", "groundnut", "arachis", "satay"
        ),
    ),
    TREE_NUT(
        "Diófélék", Group.NUTS_SEEDS, Severity.STRICT,
        "Dió, mandula, mogyoró, kesu, pisztácia.",
        "Semmilyen diaféle (dió, mandula, mogyoró, kesu, pisztácia, pekándió, makadámia).",
        listOf(
            "dió", "dio", "mandula", "mogyoró", "mogyoro", "kesu", "pisztácia", "pisztacia",
            "pekándió", "pekandio", "makadámia", "makadamia", "marcipán", "marcipan",
        ),
        // A mogyoróhagyma (salotta) hagyma, nem dióféle.
        exceptions = listOf("mogyoróhagym", "mogyorohagym"),
        en = "Tree nuts",
        noteEn = "Walnut, almond, hazelnut, cashew, pistachio.",
        ruleEn = "No tree nuts at all (walnut, almond, hazelnut, cashew, pistachio, pecan, macadamia).",
        keywordsEn = listOf(
            "walnut", "almond", "hazelnut", "cashew", "pistachio", "pecan", "macadamia", "brazil nut",
            "pine nut", "marzipan", "praline", "nut butter", "nut"
        ),
        exceptionsEn = listOf(
            "nutmeg", "coconut", "butternut", "peanut", "nutritional"
        ),
    ),
    SOY(
        "Szója", Group.NUTS_SEEDS, Severity.STRICT, "",
        "Szója, tofu, tempeh, szójaszósz, miso nem szerepelhet.",
        listOf("szója", "szoja", "tofu", "tempeh", "szójaszósz", "szojaszosz", "miso", "edamame", "tamari"),
        en = "Soy",
        noteEn = "",
        ruleEn = "No soy, tofu, tempeh, soy sauce or miso.",
        keywordsEn = listOf(
            "soy", "soya", "soybean", "tofu", "tempeh", "miso", "edamame", "tamari"
        ),
    ),
    SESAME(
        "Szezám", Group.NUTS_SEEDS, Severity.STRICT, "",
        "Szezámmag, tahini, szezámolaj nem szerepelhet.",
        listOf("szezám", "szezam", "tahini", "humusz", "hummusz", "halva"),
        en = "Sesame",
        noteEn = "",
        ruleEn = "No sesame seeds, tahini or sesame oil.",
        keywordsEn = listOf(
            "sesame", "tahini", "hummus", "houmous", "halva"
        ),
    ),

    // ---------- Egyéb allergének ----------
    MUSTARD(
        "Mustár", Group.OTHER, Severity.STRICT, "",
        "Mustár és mustármag nem szerepelhet.",
        listOf("mustár", "mustar"),
        en = "Mustard",
        noteEn = "",
        ruleEn = "No mustard or mustard seed.",
        keywordsEn = listOf(
            "mustard"
        ),
    ),
    CELERY(
        "Zeller", Group.OTHER, Severity.STRICT, "",
        "Zeller, zellerzöld és zellert tartalmazó alaplé nem szerepelhet.",
        listOf("zeller"),
        en = "Celery",
        noteEn = "",
        ruleEn = "No celery, celery leaf or stock containing celery.",
        keywordsEn = listOf(
            "celery", "celeriac"
        ),
    ),
    LUPIN(
        "Csillagfürt", Group.OTHER, Severity.STRICT, "",
        "Csillagfürtliszt és -mag nem szerepelhet.",
        listOf("csillagfürt", "csillagfurt", "lupin"),
        en = "Lupin",
        noteEn = "",
        ruleEn = "No lupin flour or lupin seed.",
        keywordsEn = listOf(
            "lupin", "lupine"
        ),
    ),
    SULPHITE(
        "Szulfit", Group.OTHER, Severity.STRICT,
        "Aszalt gyümölcsök, borok gyakori tartósítószere.",
        "Szulfitozott alapanyag (aszalt gyümölcs, bor, ecet egyes fajtái) kerülendő.",
        listOf("szulfit", "aszalt", "vörösbor", "vorosbor", "fehérbor", "feherbor", "kén-dioxid", "ken-dioxid"),
        en = "Sulphites",
        noteEn = "A common preservative in dried fruit and wine.",
        ruleEn = "Avoid sulphited ingredients (dried fruit, wine, some vinegars).",
        keywordsEn = listOf(
            "sulphite", "sulfite", "dried fruit", "raisin", "sultana", "apricot", "prune", "wine",
            "sulphur dioxide", "sulfur dioxide"
        ),
    ),

    // ---------- Intoleranciák ----------
    FRUCTOSE(
        "Fruktóz", Group.INTOLERANCE, Severity.STRICT,
        "Gyümölcscukor-felszívódási zavar.",
        "Magas fruktóztartalmú alapanyag kerülendő: méz, agavészirup, kukoricaszirup, " +
            "alma, körte, mangó, aszalt gyümölcs. Bogyós gyümölcs és banán mérsékelten adható.",
        listOf("méz", "mez", "agavészirup", "agaveszirup", "kukoricaszirup", "fruktóz", "fruktoz", "alma", "körte", "korte", "mangó", "mango"),
        en = "Fructose",
        noteEn = "Fructose malabsorption.",
        ruleEn = "Avoid high-fructose ingredients: honey, agave syrup, corn syrup, apple, pear, mango, dried fruit. Berries and banana are fine in moderation.",
        keywordsEn = listOf(
            "honey", "agave", "corn syrup", "fructose", "apple", "pear", "mango", "dried fruit",
            "raisin", "sultana", "date", "fig"
        ),
        exceptionsEn = listOf(
            "pineapple", "date palm"
        ),
    ),
    HISTAMINE(
        "Hisztamin", Group.INTOLERANCE, Severity.STRICT,
        "Érlelt, füstölt és erjesztett ételek.",
        "Érlelt sajt, felvágott, szalámi, füstölt hús, savanyú káposzta, erjesztett termékek, " +
            "bor és ecet kerülendő. Friss, aznap készült ételeket tervezz.",
        listOf(
            "szalámi", "szalami", "felvágott", "felvagott", "füstölt", "fustolt",
            "savanyú káposzta", "savanyu kaposzta", "érlelt", "erlelt", "ecet",
            "vörösbor", "vorosbor", "fehérbor", "feherbor", "kolbász", "kolbasz",
        ),
        en = "Histamine",
        noteEn = "Aged, smoked and fermented foods.",
        ruleEn = "Avoid aged cheese, cured meats, salami, smoked meat, sauerkraut, fermented products, wine and vinegar. Plan freshly cooked meals.",
        keywordsEn = listOf(
            "salami", "sausage", "cured", "smoked", "sauerkraut", "aged", "vinegar", "wine",
            "pepperoni", "prosciutto", "chorizo", "kimchi", "fermented", "anchovy", "soy sauce",
            "tomato paste"
        ),
    ),
    FODMAP(
        "FODMAP-érzékenység", Group.INTOLERANCE, Severity.STRICT,
        "IBS esetén gyakori. Az alacsony FODMAP-elvet követi.",
        "Alacsony FODMAP étrendet tervezz: hagyma, fokhagyma, búza, bab, lencse, csicseriborsó, " +
            "karfiol, alma, körte kerülendő.",
        listOf("hagyma", "fokhagyma", "bab", "lencse", "csicseriborsó", "csicseriborso", "karfiol", "alma", "körte", "korte"),
        // A babapiskóta keksz, nem hüvelyes; a toldalék miatt tőalakban.
        exceptions = listOf("babérlevél", "baberlevel", "babér", "baber", "babapiskót"),
        en = "FODMAP sensitivity",
        noteEn = "Common with IBS. Follows the low-FODMAP approach.",
        ruleEn = "Plan a low-FODMAP diet: avoid onion, garlic, wheat, beans, lentils, chickpeas, cauliflower, apple and pear.",
        keywordsEn = listOf(
            "onion", "garlic", "bean", "lentil", "chickpea", "cauliflower", "apple", "pear", "shallot",
            "leek", "wheat", "rye", "honey", "cashew", "pistachio"
        ),
        exceptionsEn = listOf(
            "pineapple", "green bean", "vanilla bean", "coffee bean", "cocoa bean"
        ),
    ),

    // ---------- Tudatos döntések ----------
    NO_PORK(
        "Sertéshús nélkül", Group.CHOICE, Severity.PREFERENCE, "",
        "Sertéshús és sertésből készült termék (szalonna, sonka, kolbász) nem szerepelhet.",
        listOf(
            "sertés", "sertes", "szalonna", "sonka", "bacon", "tarja", "karaj",
            "csülök", "csulok", "kolbász", "kolbasz", "disznó", "diszno",
            "tepertő", "teperto", "szalámi", "szalami",
        ),
        en = "No pork",
        noteEn = "",
        ruleEn = "No pork or pork products (bacon, ham, sausage).",
        keywordsEn = listOf(
            "pork", "bacon", "ham", "gammon", "prosciutto", "pancetta", "chorizo", "lard", "salami",
            "pepperoni", "pulled pork", "pork belly"
        ),
        exceptionsEn = listOf(
            "chamomile"
        ),
    ),
    NO_RED_MEAT(
        "Vörös hús nélkül", Group.CHOICE, Severity.PREFERENCE,
        "Baromfi és hal maradhat.",
        "Marha, sertés, bárány, borjú és vadhús nem szerepelhet. Baromfi és hal használható.",
        listOf(
            "marha", "sertés", "sertes", "bárány", "barany", "birka", "borjú", "borju",
            "vadhús", "vadhus", "szarvas", "őz", "szalonna", "sonka", "disznó", "diszno",
            "kolbász", "kolbasz", "szalámi", "szalami", "tepertő", "teperto",
            "tarja", "karaj", "csülök", "csulok",
        ),
        en = "No red meat",
        noteEn = "Poultry and fish stay in.",
        ruleEn = "No beef, pork, lamb, veal or game. Poultry and fish are fine.",
        keywordsEn = listOf(
            "beef", "pork", "lamb", "mutton", "veal", "venison", "steak", "mince", "bacon", "ham",
            "sausage", "brisket", "sirloin", "ribeye", "oxtail"
        ),
        exceptionsEn = listOf(
            "chamomile", "beefsteak tomato"
        ),
    ),
    NO_POULTRY(
        "Baromfi nélkül", Group.CHOICE, Severity.PREFERENCE, "",
        "Csirke, pulyka, kacsa, liba nem szerepelhet.",
        listOf("csirke", "pulyka", "kacsa", "liba", "baromfi"),
        en = "No poultry",
        noteEn = "",
        ruleEn = "No chicken, turkey, duck or goose.",
        keywordsEn = listOf(
            "chicken", "turkey", "duck", "goose", "poultry", "quail"
        ),
    ),
    NO_ALCOHOL(
        "Alkoholmentes", Group.CHOICE, Severity.PREFERENCE,
        "Főzéshez sem.",
        "Semmilyen alkoholtartalmú alapanyag, főzéshez használt bor vagy sör sem.",
        listOf(
            "vörösbor", "vorosbor", "fehérbor", "feherbor", "főzőbor", "fozobor", "sör",
            "rum", "konyak", "likőr", "likor", "vodka", "pálinka", "palinka", "whisky", "pezsgő", "pezsgo",
        ),
        en = "Alcohol-free",
        noteEn = "Not even for cooking.",
        ruleEn = "No alcoholic ingredient at all, not even wine or beer used in cooking.",
        keywordsEn = listOf(
            "wine", "beer", "rum", "brandy", "cognac", "liqueur", "vodka", "whisky", "whiskey",
            "champagne", "prosecco", "sherry", "vermouth", "cider", "bourbon"
        ),
        exceptionsEn = listOf(
            "vinegar", "wine vinegar", "cider vinegar"
        ),
    ),
    HALAL(
        "Halal", Group.CHOICE, Severity.PREFERENCE, "",
        "Sertés, sertészsír, zselatin és alkohol nem szerepelhet; a hús halal legyen.",
        listOf(
            "sertés", "sertes", "szalonna", "sonka", "bacon", "zselatin", "vörösbor",
            "vorosbor", "sör", "rum", "disznó", "diszno", "kolbász", "kolbasz",
            "szalámi", "szalami", "tepertő", "teperto",
        ),
        en = "Halal",
        noteEn = "",
        ruleEn = "No pork, lard, gelatine or alcohol; meat must be halal.",
        keywordsEn = listOf(
            "pork", "bacon", "ham", "gammon", "lard", "gelatine", "gelatin", "wine", "beer", "rum",
            "prosciutto", "pancetta", "chorizo"
        ),
        exceptionsEn = listOf(
            "chamomile"
        ),
    ),
    KOSHER(
        "Kóser", Group.CHOICE, Severity.PREFERENCE,
        "Sertés és a hús-tej együttes használata kizárva.",
        "Sertés, rákfélék, puhatestűek nem szerepelhetnek, és egy fogásban ne legyen együtt hús és tejtermék.",
        // „rak" nincs a listán: lásd a CRUSTACEAN indoklását — a rakott krumpli nem rák.
        listOf("sertés", "sertes", "szalonna", "sonka", "bacon", "rák", "garnéla", "garnela", "kagyló", "kagylo"),
        exceptions = listOf("rákóczi", "rakoczi"),
        en = "Kosher",
        noteEn = "No pork, and no meat and dairy in the same dish.",
        ruleEn = "No pork, crustaceans or molluscs, and do not put meat and dairy in the same dish.",
        keywordsEn = listOf(
            "pork", "bacon", "ham", "gammon", "lard", "crab", "prawn", "shrimp", "lobster", "mussel",
            "clam", "squid", "octopus", "oyster", "scallop"
        ),
        exceptionsEn = listOf(
            "chamomile"
        ),
    );

    fun note(language: AppLanguage): String =
        if (language == AppLanguage.EN) noteEn else note

    fun rule(language: AppLanguage): String =
        if (language == AppLanguage.EN) ruleEn else rule

    fun keywords(language: AppLanguage): List<String> =
        if (language == AppLanguage.EN) keywordsEn else keywords

    fun safeMarkers(language: AppLanguage): List<String> =
        if (language == AppLanguage.EN) safeMarkersEn else safeMarkers

    fun exceptions(language: AppLanguage): List<String> =
        if (language == AppLanguage.EN) exceptionsEn else exceptions

    enum class Group(override val hu: String, override val en: String) : Localized {
        CEREAL_DAIRY("Gabona és tej", "Grains and dairy"),
        ANIMAL("Állati eredetű allergének", "Animal allergens"),
        NUTS_SEEDS("Magvak, hüvelyesek", "Nuts and seeds"),
        OTHER("Egyéb allergének", "Other allergens"),
        INTOLERANCE("Intoleranciák", "Intolerances"),
        CHOICE("Étrendi döntések", "Dietary choices"),
    }

    enum class Severity { STRICT, PREFERENCE }

    companion object {
        fun byName(raw: String): DietRestriction? = entries.firstOrNull { it.name == raw }

        fun byGroup(): Map<Group, List<DietRestriction>> = entries.groupBy { it.group }

        /** Az étrendi stílusból következő kizárások — ezeket nem kell külön kipipálni. */
        fun impliedBy(style: DietStyle): Set<DietRestriction> = when (style) {
            DietStyle.VEGAN -> setOf(MILK_PROTEIN, LACTOSE, EGG, FISH, CRUSTACEAN, MOLLUSC, NO_RED_MEAT, NO_POULTRY)
            DietStyle.VEGETARIAN -> setOf(FISH, CRUSTACEAN, MOLLUSC, NO_RED_MEAT, NO_POULTRY)
            DietStyle.PESCATARIAN -> setOf(NO_RED_MEAT, NO_POULTRY)
            else -> emptySet()
        }
    }
}
