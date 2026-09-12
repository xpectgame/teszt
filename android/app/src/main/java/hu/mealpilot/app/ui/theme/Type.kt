package hu.mealpilot.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import hu.mealpilot.app.R

/**
 * Címek, köszönés, nagy számok. Ez tartja komolyan a lágy formákat: a nagy
 * lekerekítések egy groteszkkel gyerekesbe csúsznának.
 */
val Newsreader = FontFamily(
    Font(R.font.newsreader_medium, FontWeight.Normal),
    Font(R.font.newsreader_medium, FontWeight.Medium),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold),
    Font(R.font.newsreader_semibold, FontWeight.Bold),
)

/** Minden más felületi szöveg. */
val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold),
)

// A betűk fölé-alá lógó része miatt a Compose alapból egyenlőtlen térközt hagy a
// sor tetején és alján. Ez a beállítás a sormagasságot egyenletesen osztja el,
// így a kártyák szövege tényleg középen ül.
private val evenLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun display(size: Double, lineHeight: Double, weight: FontWeight = FontWeight.Medium) = TextStyle(
    fontFamily = Newsreader,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    lineHeightStyle = evenLines,
)

private fun ui(
    size: Double,
    lineHeight: Double,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = Archivo,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = evenLines,
)

/**
 * A méretek a vászonról jönnek (design/Alapok.dc.html, Main.dc.html). A CSS px és
 * az sp nem ugyanaz — a felhasználó felnagyíthatja a rendszer betűméretét —, de a
 * kiindulási arányok innen származnak.
 */
val PlateTypography = Typography(
    displayLarge = display(54.0, 48.0),        // a hős doboz nagy száma
    displayMedium = display(44.0, 46.0),
    displaySmall = display(36.0, 39.0),
    headlineLarge = display(31.0, 34.0),       // „Szia, Máté!"
    headlineMedium = display(26.0, 30.0),
    headlineSmall = display(22.0, 26.0),
    titleLarge = display(19.0, 24.0),           // szakaszcím: „Mai étkezések"
    titleMedium = ui(16.0, 21.0, FontWeight.SemiBold),
    titleSmall = ui(14.0, 19.0, FontWeight.SemiBold),
    bodyLarge = ui(15.0, 22.0),
    bodyMedium = ui(14.0, 21.0),
    bodySmall = ui(12.5, 19.0),
    labelLarge = ui(14.0, 18.0, FontWeight.SemiBold),
    labelMedium = ui(12.0, 16.0, FontWeight.Medium),
    labelSmall = ui(11.0, 15.0, FontWeight.Medium),
)

/**
 * A táblázatos számjegy: minden számnak azonos a szélessége, így a tápértékek nem
 * ugrálnak, amikor 9-ről 10-re vált egy érték. A Compose `fontFeatureSettings`
 * mezőjén keresztül kapcsoljuk be, mert a `tnum` OpenType-jellemző.
 */
const val TabularNums = "tnum"

/** Étkezéscímke: apró, ritkított nagybetű — „REGGELI". */
val MealLabelStyle = ui(10.5, 14.0, FontWeight.Bold, tracking = 1.0)
