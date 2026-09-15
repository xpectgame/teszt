package hu.mealpilot.core.ai

import hu.mealpilot.core.i18n.AppLanguage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Amit a felhasználó szavakkal elmondott étkezéséről kitalálunk.
 *
 * Szándékosan szűk: név és négy szám. A naplózás nem tervezés — itt nem recept kell,
 * hanem az, hogy a mai keretbe beleférjen-e, amit megevett.
 */
@Serializable
data class AiMealEstimate(
    val name: String = "",
    val kcal: Double = 0.0,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
    /** Egy rövid mondat arról, mit feltételezett — adagméret, elkészítés. */
    val assumption: String = "",
) {
    /** Használható-e egyáltalán. A 0 kcal-os becslés nem becslés, hanem kudarc. */
    val isUsable: Boolean get() = name.isNotBlank() && kcal > 0
}

object EstimatePrompts {

    val SYSTEM: String = """
Egy táplálkozási napló segédje vagy. A felhasználó szavakkal mondja el, mit evett, te
pedig megbecsülöd a tápértékét.

MIT CSINÁLSZ
- Kitalálod, mi az étel, és megbecsülöd a kalóriát, a fehérjét, a szénhidrátot és a zsírt.
- Ha nincs megadva adag, a szokásos egy adaggal számolsz, és ezt leírod az "assumption"
  mezőben. Ha az adag meg van adva, azzal.
- A "name" rövid, felismerhető név, nagybetűvel kezdve. Nem mondat.
- MAGYARUL nevezd el, akkor is, ha a felhasználó angolul írta: cottage cheese → Túró,
  greek yogurt → Görög joghurt, peanut butter → Mogyoróvaj. A meghonosodott szavak
  (smoothie, wrap, quinoa) maradhatnak. Az "assumption" is gondozott magyar mondat.

PONTOSSÁG
- Ez becslés, nem laboratóriumi mérés. A jó becslés hasznosabb, mint a pontatlanság
  miatti visszakérdezés — ne kérdezz vissza, tippelj a legvalószínűbbre.
- A makrók összhangban legyenek a kalóriával: fehérje 4, szénhidrát 4, zsír 9 kcal
  grammonként. A hármukból számolt érték a kcal ±15%-án belül maradjon.
- Ha a szöveg nem étel (üres, értelmetlen, vagy nem ehető), a "name" maradjon üres és a
  kcal 0 — ebből tudja az app, hogy nem sikerült.

VÁLASZ FORMÁTUMA
Kizárólag egyetlen JSON objektum, magyarázat és kódkerítés nélkül:
{
  "name": "Gyrosos pita",
  "kcal": 720,
  "protein_g": 34,
  "carbs_g": 78,
  "fat_g": 30,
  "assumption": "Egy közepes adaggal, tzatzikivel számolva."
}
""".trimIndent()

    val SYSTEM_EN: String = """
You are the helper of a food diary. The user describes in words what they ate, and you
estimate its nutrition.

WHAT YOU DO
- Work out what the food is, and estimate calories, protein, carbohydrate and fat.
- If no portion is given, assume one usual serving and say so in the "assumption" field.
  If a portion is given, use that.
- "name" is a short, recognisable name starting with a capital letter. Not a sentence.

ACCURACY
- This is an estimate, not a lab measurement. A good estimate is more useful than asking
  the user to be more precise — do not ask back, guess the most likely case.
- Keep the macros consistent with the calories: protein 4, carbohydrate 4, fat 9 kcal per
  gram. The value computed from the three should stay within ±15% of kcal.
- If the text is not food (empty, meaningless, or inedible), leave "name" empty and kcal
  at 0 — that is how the app knows it failed.

RESPONSE FORMAT
A single JSON object, with no prose and no code fences:
{
  "name": "Chicken gyros wrap",
  "kcal": 720,
  "protein_g": 34,
  "carbs_g": 78,
  "fat_g": 30,
  "assumption": "Assuming one medium serving with tzatziki."
}
""".trimIndent()

    fun system(language: AppLanguage): String =
        if (language == AppLanguage.EN) SYSTEM_EN else SYSTEM

    fun userPrompt(description: String, language: AppLanguage): String =
        if (language == AppLanguage.EN) {
            "The user ate this:\n${description.trim()}"
        } else {
            "A felhasználó ezt ette:\n${description.trim()}"
        }
}
