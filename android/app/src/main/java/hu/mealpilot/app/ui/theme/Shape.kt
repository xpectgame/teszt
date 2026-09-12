package hu.mealpilot.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * A lekerekítések a vászonról: 30 hős doboz, 26 kártya, 19 lapka, 16 gomb.
 *
 * A Material 3 a `Shapes` öt fokozatát osztja szét a komponensek között, ezért a
 * négy tervezett érték ide képződik le. Az `extraSmall` a legkisebb belső elemeké
 * (jelölődoboz, apró jelvény), azt 12-n hagyjuk.
 */
val PlateShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),      // gomb
    medium = RoundedCornerShape(19.dp),     // lapka
    large = RoundedCornerShape(26.dp),      // kártya
    extraLarge = RoundedCornerShape(30.dp), // hős doboz
)

/** Nevesített formák ott, ahol a Material fokozat neve semmit nem mondana. */
object PlateShape {
    val hero = RoundedCornerShape(30.dp)
    val card = RoundedCornerShape(26.dp)
    val tile = RoundedCornerShape(19.dp)
    val button = RoundedCornerShape(16.dp)
    val innerButton = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(percent = 50)
}
