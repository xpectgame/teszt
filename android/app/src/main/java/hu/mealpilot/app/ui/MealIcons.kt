package hu.mealpilot.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.ui.graphics.vector.ImageVector
import hu.mealpilot.core.ai.MealSlot

/**
 * Étkezésenként külön ikon.
 *
 * A szín önmagában kevés volt: a bélyegen mind a hat étkezés ugyanazt a tányérjelet
 * viselte, tehát egy pillantásra csak az árnyalat különböztette meg őket — színtévesztő
 * szemmel pedig semmi. Az ikon a napszakról is mond valamit, nem csak arról, hogy
 * „ez egy másik étkezés".
 *
 * Vonalas (Outlined) készlet, mert a bélyeg telített színén a tömör ikon folttá válna.
 */
val MealSlot.icon: ImageVector
    get() = when (this) {
        MealSlot.BREAKFAST -> Icons.Outlined.FreeBreakfast      // bögre
        MealSlot.MORNING_SNACK -> Icons.Outlined.BakeryDining   // péksütemény
        MealSlot.LUNCH -> Icons.Outlined.LunchDining            // tányér, szendvics
        MealSlot.AFTERNOON_SNACK -> Icons.Outlined.Cookie       // keksz
        MealSlot.DINNER -> Icons.Outlined.DinnerDining          // tál, evőeszköz
        MealSlot.EVENING_SNACK -> Icons.Outlined.NightsStay     // hold
    }
