package hu.mealpilot.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A „Tányér" irány rögzített palettája (design/Alapok.dc.html).
 *
 * Szándékosan NEM a Material 3 dinamikus szín: az minden telefonon a háttérképből
 * veszi a színeket, tehát az app sehol nem néz ki úgy, ahogy megterveztük — és a
 * krém–zöld–agyag hármas pont az, amitől MealPilot-nak látszik.
 */
object Plate {
    // Világos
    val paper = Color(0xFFFFF6EE)
    val card = Color(0xFFFFFFFF)
    val ink = Color(0xFF2A1E16)
    // A vászon #8A7566-ot adott, az viszont krém alapon csak 4,08:1 — apró szövegre
    // (tápértéksor, időpont, kiegészítő felirat) ez a WCAG AA 4,5:1 alatt van, és
    // pont ezeket a feliratokat viseli. A legkisebb sötétítés, ami krémen ÉS fehéren
    // is átmegy: #826E60 (4,52 / 4,83). A karaktere ugyanaz a meleg szürkésbarna.
    val ink2 = Color(0xFF826E60)
    val line = Color(0xFFF0E0D2)
    val green = Color(0xFF2F7D5E)
    val greenSoft = Color(0xFFE2F1E9)
    val clay = Color(0xFFE4733F)
    val claySoft = Color(0xFFFDE7DA)

    // Sötét
    val paperDark = Color(0xFF191310)
    val cardDark = Color(0xFF241C17)
    val inkDark = Color(0xFFF6EBE2)
    val ink2Dark = Color(0xFFA8917F)
    val lineDark = Color(0xFF33261E)
    val greenDark = Color(0xFF6FC79E)
    val greenSoftDark = Color(0xFF1E3A2E)
    val clayDark = Color(0xFFF0996A)
    val claySoftDark = Color(0xFF3A2318)

    val error = Color(0xFFBA1A1A)
    val errorDark = Color(0xFFFFB4AB)
}

/**
 * Étkezésszínek. A lapkák ebből kapják a színüket — ez az egyetlen játékosság a
 * felületen, a betűk szándékosan komolyak maradnak.
 *
 * A sorrend a nap menete: reggeli, tízórai, ebéd, uzsonna, vacsora, esti falat.
 * A vászon öt színt nevezett meg; a hatodik (palakék) azért kellett, mert a
 * MealSlot hat értéket ismer, és körbefordulva az esti falat pont a reggeli
 * színét kapta volna — két étkezés ugyanazzal a színnel épp azt rontaná el,
 * amiért a színek egyáltalán ott vannak.
 */
object MealColors {
    private val light = listOf(
        Color(0xFFE4733F), // reggeli — agyag
        Color(0xFFE0A63C), // tízórai — borostyán
        Color(0xFF2F7D5E), // ebéd — zöld
        Color(0xFFC4553C), // uzsonna — tégla
        Color(0xFF8A6BB1), // vacsora — szilva
        Color(0xFF5B7FA6), // esti falat — palakék
    )

    // Sötét háttéren a világos változatok kellenek, különben a fehér ikon elveszne
    // a telített színen, és a sötét alaphoz képest is túl zajos lenne.
    private val dark = listOf(
        Color(0xFFF0996A),
        Color(0xFFEFC470),
        Color(0xFF6FC79E),
        Color(0xFFE0836B),
        Color(0xFFB79BD8),
        Color(0xFF9DBBD8),
    )

    fun of(index: Int, darkTheme: Boolean): Color {
        val palette = if (darkTheme) dark else light
        // A negatív indexet is elbírja: a rem operátor előjelet tart Kotlinban.
        return palette[((index % palette.size) + palette.size) % palette.size]
    }

    /**
     * Mi látszik EZEN az étkezésszínen: a tányér jele és minden rárajzolt elem.
     *
     * Fehér nem elég. A borostyán már világos témában is csak 2,17:1 fehérrel,
     * sötét témában pedig mind a hat szín világos pasztell — ott a fehér jel
     * gyakorlatilag eltűnne a bélyegen.
     *
     * Ezért a tinta és a fehér közül azt választjuk, amelyik jobban elüt. A hat
     * világos és hat sötét étkezésszínen a legrosszabb eset így 4,17:1, a korábbi
     * 1,64:1 helyett. A döntést a szín hozza, nem a téma: sötét témában is lehet
     * sötét étkezésszín, és fordítva.
     */
    fun contentOn(color: Color): Color =
        if (contrast(Plate.ink, color) >= contrast(Color.White, color)) Plate.ink else Color.White

    /** WCAG kontrasztarány két szín között: 1:1 (azonos) és 21:1 (fekete-fehér) között. */
    private fun contrast(a: Color, b: Color): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** WCAG relatív világosság. */
    private fun relativeLuminance(color: Color): Float {
        fun channel(v: Float): Float =
            if (v <= 0.03928f) v / 12.92f
            else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * channel(color.red) +
            0.7152f * channel(color.green) +
            0.0722f * channel(color.blue)
    }
}

/** A kalóriakeret állapotát jelző színek — a felületen több helyen ugyanezt használjuk. */
object BudgetColors {
    val under = Color(0xFF2F7D5E)
    val close = Color(0xFFE0A63C)
    val over = Color(0xFFC4553C)
}
