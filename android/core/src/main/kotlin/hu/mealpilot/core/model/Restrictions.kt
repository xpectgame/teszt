package hu.mealpilot.core.model

/**
 * Étrendi kizárások: allergiák, intoleranciák és tudatos döntések.
 *
 * Minden tétel hoz magával egy magyar kulcsszólistát is. Ez azért kell, mert az AI-ra
 * egy allergiát nem szabad rábízni: a legenerált étrend hozzávalóit gépileg is átnézzük
 * ([hu.mealpilot.core.ai.RestrictionChecker]), és találat esetén a terv nem jut el a
 * felhasználóig, hanem újratervezést kér.
 */
enum class DietRestriction(
    val hu: String,
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
) {

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
        ),
        safeMarkers = listOf("gluténmentes", "glutenmentes", "gluténmentesen", "gm "),
    ),
    LACTOSE(
        "Laktóz", Group.CEREAL_DAIRY, Severity.STRICT,
        "A laktózmentes tejtermék engedélyezett marad.",
        "Laktóztartalmú tejtermék nem szerepelhet; laktózmentes változat elfogadható.",
        listOf(
            "tej", "tejföl", "tejfol", "tejszín", "tejszin", "joghurt", "sajt", "vaj", "túró", "turo",
            "kefir", "író", "mascarpone", "ricotta", "feta", "mozzarella", "parmezán", "parmezan",
            "camembert", "cottage", "tejpor", "körözött", "korozott",
        ),
        safeMarkers = listOf("laktózmentes", "laktozmentes", "növényi", "novenyi", "zabtej", "szójatej", "szojatej", "mandulatej", "rizstej"),
    ),
    MILK_PROTEIN(
        "Tejfehérje (kazein)", Group.CEREAL_DAIRY, Severity.STRICT,
        "Ez szigorúbb a laktózérzékenységnél: a laktózmentes tej is tiltott.",
        "Semmilyen tejeredetű alapanyag, a laktózmentes változatok sem (azok is tartalmaznak " +
            "kazeint és tejsavófehérjét). Növényi alternatíva használható.",
        listOf(
            "tej", "tejföl", "tejfol", "tejszín", "tejszin", "joghurt", "sajt", "vaj", "túró", "turo",
            "kefir", "író", "mascarpone", "ricotta", "feta", "mozzarella", "parmezán", "parmezan",
            "camembert", "cottage", "tejpor", "kazein", "tejsavó", "tejsavo", "körözött", "korozott",
        ),
        safeMarkers = listOf("növényi", "novenyi", "zabtej", "szójatej", "szojatej", "mandulatej", "rizstej", "kókusztej", "kokusztej"),
    ),

    // ---------- Állati eredetű ----------
    EGG(
        "Tojás", Group.ANIMAL, Severity.STRICT, "",
        "Tojás és tojástartalmú termék (majonéz, tojásos tészta) nem szerepelhet.",
        listOf("tojás", "tojas", "majonéz", "majonez", "tojásfehérje", "tojassárgája", "rántotta", "rantotta", "omlett"),
    ),
    FISH(
        "Hal", Group.ANIMAL, Severity.STRICT, "",
        "Semmilyen hal, halkészítmény, halszósz vagy halolaj.",
        listOf(
            "hal", "lazac", "tonhal", "pisztráng", "pisztrang", "hering", "szardínia", "szardinia",
            "tőkehal", "tokehal", "harcsa", "ponty", "szardella", "makréla", "makrela", "halszósz", "halszosz",
        ),
    ),
    CRUSTACEAN(
        "Rákfélék", Group.ANIMAL, Severity.STRICT, "",
        "Rák, garnéla, homár és minden rákféle tiltott.",
        listOf("rák", "rak", "garnéla", "garnela", "homár", "homar", "languszta", "scampi"),
    ),
    MOLLUSC(
        "Puhatestűek", Group.ANIMAL, Severity.STRICT, "",
        "Kagyló, tintahal, polip, csiga nem szerepelhet.",
        listOf("kagyló", "kagylo", "tintahal", "polip", "csiga", "osztriga", "kalamári", "kalamari"),
    ),

    // ---------- Magvak, hüvelyesek ----------
    PEANUT(
        "Földimogyoró", Group.NUTS_SEEDS, Severity.STRICT, "",
        "Földimogyoró, mogyoróvaj, arachisolaj nem szerepelhet.",
        listOf("földimogyoró", "foldimogyoro", "mogyoróvaj", "mogyorovaj", "arachis", "arasz"),
    ),
    TREE_NUT(
        "Diófélék", Group.NUTS_SEEDS, Severity.STRICT,
        "Dió, mandula, mogyoró, kesu, pisztácia.",
        "Semmilyen diaféle (dió, mandula, mogyoró, kesu, pisztácia, pekándió, makadámia).",
        listOf(
            "dió", "dio", "mandula", "mogyoró", "mogyoro", "kesu", "pisztácia", "pisztacia",
            "pekándió", "pekandio", "makadámia", "makadamia", "marcipán", "marcipan",
        ),
    ),
    SOY(
        "Szója", Group.NUTS_SEEDS, Severity.STRICT, "",
        "Szója, tofu, tempeh, szójaszósz, miso nem szerepelhet.",
        listOf("szója", "szoja", "tofu", "tempeh", "szójaszósz", "szojaszosz", "miso", "edamame"),
    ),
    SESAME(
        "Szezám", Group.NUTS_SEEDS, Severity.STRICT, "",
        "Szezámmag, tahini, szezámolaj nem szerepelhet.",
        listOf("szezám", "szezam", "tahini", "humusz", "hummusz"),
    ),

    // ---------- Egyéb allergének ----------
    MUSTARD(
        "Mustár", Group.OTHER, Severity.STRICT, "",
        "Mustár és mustármag nem szerepelhet.",
        listOf("mustár", "mustar"),
    ),
    CELERY(
        "Zeller", Group.OTHER, Severity.STRICT, "",
        "Zeller, zellerzöld és zellert tartalmazó alaplé nem szerepelhet.",
        listOf("zeller"),
    ),
    LUPIN(
        "Csillagfürt", Group.OTHER, Severity.STRICT, "",
        "Csillagfürtliszt és -mag nem szerepelhet.",
        listOf("csillagfürt", "csillagfurt", "lupin"),
    ),
    SULPHITE(
        "Szulfit", Group.OTHER, Severity.STRICT,
        "Aszalt gyümölcsök, borok gyakori tartósítószere.",
        "Szulfitozott alapanyag (aszalt gyümölcs, bor, ecet egyes fajtái) kerülendő.",
        listOf("szulfit", "aszalt", "vörösbor", "vorosbor", "fehérbor", "feherbor", "kén-dioxid", "ken-dioxid"),
    ),

    // ---------- Intoleranciák ----------
    FRUCTOSE(
        "Fruktóz", Group.INTOLERANCE, Severity.STRICT,
        "Gyümölcscukor-felszívódási zavar.",
        "Magas fruktóztartalmú alapanyag kerülendő: méz, agavészirup, kukoricaszirup, " +
            "alma, körte, mangó, aszalt gyümölcs. Bogyós gyümölcs és banán mérsékelten adható.",
        listOf("méz", "mez", "agavészirup", "agaveszirup", "kukoricaszirup", "fruktóz", "fruktoz", "alma", "körte", "korte", "mangó", "mango"),
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
    ),
    FODMAP(
        "FODMAP-érzékenység", Group.INTOLERANCE, Severity.STRICT,
        "IBS esetén gyakori. Az alacsony FODMAP-elvet követi.",
        "Alacsony FODMAP étrendet tervezz: hagyma, fokhagyma, búza, bab, lencse, csicseriborsó, " +
            "karfiol, alma, körte kerülendő.",
        listOf("hagyma", "fokhagyma", "bab", "lencse", "csicseriborsó", "csicseriborso", "karfiol", "alma", "körte", "korte"),
        exceptions = listOf("babérlevél", "baberlevel", "babér", "baber"),
    ),

    // ---------- Tudatos döntések ----------
    NO_PORK(
        "Sertéshús nélkül", Group.CHOICE, Severity.PREFERENCE, "",
        "Sertéshús és sertésből készült termék (szalonna, sonka, kolbász) nem szerepelhet.",
        listOf("sertés", "sertes", "szalonna", "sonka", "bacon", "tarja", "karaj", "csülök", "csulok", "kolbász", "kolbasz"),
    ),
    NO_RED_MEAT(
        "Vörös hús nélkül", Group.CHOICE, Severity.PREFERENCE,
        "Baromfi és hal maradhat.",
        "Marha, sertés, bárány, borjú és vadhús nem szerepelhet. Baromfi és hal használható.",
        listOf("marha", "sertés", "sertes", "bárány", "barany", "birka", "borjú", "borju", "vadhús", "vadhus", "szarvas", "őz", "szalonna", "sonka"),
    ),
    NO_POULTRY(
        "Baromfi nélkül", Group.CHOICE, Severity.PREFERENCE, "",
        "Csirke, pulyka, kacsa, liba nem szerepelhet.",
        listOf("csirke", "pulyka", "kacsa", "liba", "baromfi"),
    ),
    NO_ALCOHOL(
        "Alkoholmentes", Group.CHOICE, Severity.PREFERENCE,
        "Főzéshez sem.",
        "Semmilyen alkoholtartalmú alapanyag, főzéshez használt bor vagy sör sem.",
        listOf(
            "vörösbor", "vorosbor", "fehérbor", "feherbor", "főzőbor", "fozobor", "sör",
            "rum", "konyak", "likőr", "likor", "vodka", "pálinka", "palinka", "whisky", "pezsgő", "pezsgo",
        ),
    ),
    HALAL(
        "Halal", Group.CHOICE, Severity.PREFERENCE, "",
        "Sertés, sertészsír, zselatin és alkohol nem szerepelhet; a hús halal legyen.",
        listOf("sertés", "sertes", "szalonna", "sonka", "bacon", "zselatin", "vörösbor", "vorosbor", "sör", "rum"),
    ),
    KOSHER(
        "Kóser", Group.CHOICE, Severity.PREFERENCE,
        "Sertés és a hús-tej együttes használata kizárva.",
        "Sertés, rákfélék, puhatestűek nem szerepelhetnek, és egy fogásban ne legyen együtt hús és tejtermék.",
        listOf("sertés", "sertes", "szalonna", "sonka", "bacon", "rák", "rak", "garnéla", "garnela", "kagyló", "kagylo"),
    );

    enum class Group(val hu: String) {
        CEREAL_DAIRY("Gabona és tej"),
        ANIMAL("Állati eredetű allergének"),
        NUTS_SEEDS("Magvak, hüvelyesek"),
        OTHER("Egyéb allergének"),
        INTOLERANCE("Intoleranciák"),
        CHOICE("Étrendi döntések"),
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
