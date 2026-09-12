package hu.mealpilot.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.mealpilot.app.ui.theme.PlateShape
import hu.mealpilot.app.ui.theme.TabularNums
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A „Tányér" irány visszatérő elemei. Azért itt vannak és nem a képernyőkben, mert
 * a lapka és a jelvény több helyen is kell (Ma, Terv, Étkezés részletei), és ha
 * mindenhol külön születne újra, két hét múlva három különböző lapkánk lenne.
 */

/** Táblázatos számjegy: a tápértékek ne ugráljanak, amikor 9-ről 10-re vált egy érték. */
@Composable
fun NumberText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
) = Text(
    text = text,
    modifier = modifier,
    style = style.copy(fontFeatureSettings = TabularNums),
    color = color,
)

/**
 * A napi keret hős doboza: mély zöld lap, benne a legnagyobb szám a képernyőn.
 *
 * A makrók szándékosan IDE kerültek, a nagy szám mellé. A nagy lapkák miatt
 * kevesebb fér a képernyőre, tehát ha a makrók külön kártyán élnének, görgetni
 * kellene értük — pont annak, aki számol.
 */
@Composable
fun BudgetHero(
    consumedKcal: Int,
    targetKcal: Int,
    labels: HeroLabels,
    modifier: Modifier = Modifier,
) {
    val remaining by animateIntAsState(
        targetValue = targetKcal - consumedKcal,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "hero-remaining",
    )
    val fraction by animateFloatAsState(
        targetValue = if (targetKcal <= 0) 0f else (consumedKcal.toFloat() / targetKcal).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "hero-progress",
    )
    val over = remaining < 0
    val onHero = MaterialTheme.colorScheme.onPrimary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PlateShape.hero,
        color = MaterialTheme.colorScheme.primary,
        contentColor = onHero,
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    NumberText(
                        text = abs(remaining).toString(),
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (over) labels.overLabel else labels.leftLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = onHero.copy(alpha = 0.8f),
                    )
                }
                Column(
                    Modifier.widthIn(min = 124.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    labels.lines.forEach { line ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                line.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = onHero.copy(alpha = 0.8f),
                            )
                            NumberText(
                                "${line.current.roundToInt()} / ${line.target}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            // Saját sáv, nem LinearProgressIndicator: az a saját színsémájából veszi a
            // hátteret, itt viszont a zöld lapon a fehér áttetsző változata kell.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(PlateShape.pill)
                    .background(onHero.copy(alpha = 0.26f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(9.dp)
                        .clip(PlateShape.pill)
                        .background(onHero.copy(alpha = 0.92f)),
                )
            }
            Spacer(Modifier.height(8.dp))
            NumberText(
                labels.totalLabel,
                style = MaterialTheme.typography.labelSmall,
                color = onHero.copy(alpha = 0.75f),
            )
        }
    }
}

/** Egy makró sora a hős dobozban. */
data class MacroLine(val label: String, val current: Double, val target: Int)

/**
 * A hős doboz feliratai. Külön típus, mert a szövegek erőforrásból jönnek — a
 * komponens így nem függ az `R` osztálytól, és a vizuális előnézet is működik.
 */
data class HeroLabels(
    val lines: List<MacroLine>,
    val leftLabel: String,
    val overLabel: String,
    val totalLabel: String,
)

/** Szakaszcím: bal oldalt serif cím, jobb oldalt halvány számláló. */
@Composable
fun SectionHeading(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (trailing != null) {
            NumberText(
                trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Az étkezés színes bélyege: lekerekített négyzet a tányér jelével, sarkában pipa,
 * ha már megette. A pipa körüli gyűrű a kártya színével rajzolódik, hogy a bélyeg
 * szélétől elváljon.
 */
@Composable
fun MealStamp(
    color: Color,
    done: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(58.dp)) {
        Box(
            Modifier
                .size(58.dp)
                .clip(PlateShape.tile)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            PlateMark(Modifier.size(26.dp), Color.White)
        }
        if (done) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** A tányér jele: két koncentrikus kör, ugyanaz, mint az app ikonján. */
@Composable
private fun PlateMark(modifier: Modifier = Modifier, tint: Color) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * 0.058f)
        drawCircle(color = tint, radius = size.minDimension * 0.342f, style = stroke)
        drawCircle(color = tint, radius = size.minDimension * 0.142f, style = stroke)
    }
}

/** Kis pirula-jelvény: „1 980 kcal/nap", „7 nap". */
@Composable
fun PlatePill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier
            .clip(PlateShape.pill)
            .background(container)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        NumberText(
            text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = content,
        )
    }
}

/** Krém hátterű, halvány keretes doboz — ott, ahol kártya kell, de nem hangsúlyos. */
@Composable
fun QuietBox(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(PlateShape.card)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PlateShape.card)
            .padding(16.dp),
        content = content,
    )
}

/**
 * Figyelmeztető sor. Eddig egy „⚠" karakter állt a szöveg előtt — az a rendszer
 * betűkészletétől függően hol emoji, hol szimbólum, hol semmi, és a képernyőolvasó
 * „figyelmeztetés" helyett a nevét mondja ki. Ikonnal mindhárom megoldódik.
 */
@Composable
fun WarningNote(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.error,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/**
 * Vissza gomb. Három helyen élt három változatban: „← Vissza" szöveges nyíllal,
 * „Vissza" nyíl nélkül, és egy ikon + két szóköz + szöveg. A nyíl karakterként
 * ráadásul nem fordul meg jobbról balra író nyelven, az AutoMirrored ikon igen.
 */
@Composable
fun BackButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(text)
    }
}

@Composable
fun CenteredNote(text: String, modifier: Modifier = Modifier) = Text(
    text = text,
    modifier = modifier.fillMaxWidth(),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)
