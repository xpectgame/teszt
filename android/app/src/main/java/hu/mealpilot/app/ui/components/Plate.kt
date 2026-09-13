package hu.mealpilot.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.mealpilot.app.ui.theme.MealColors
import hu.mealpilot.app.ui.theme.MealLabelStyle
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
    maxLines: Int = Int.MAX_VALUE,
) = Text(
    text = text,
    modifier = modifier,
    style = style.copy(fontFeatureSettings = TabularNums),
    color = color,
    maxLines = maxLines,
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
                        // Egy sor, mindig. Nagy rendszerbetűvel a szám kinőné a
                        // fél hasábot, és tördelve minden számjegy külön sorba
                        // kerülne — abból torony lesz, nem szám.
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (over) labels.overLabel else labels.leftLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = onHero.copy(alpha = 0.8f),
                    )
                }
                // A makróhasáb SÚLYOZOTT, nem `widthIn(min=…)`. Súly nélkül a Compose a
                // teljes elérhető szélességgel méri, a benne lévő sorok pedig
                // fillMaxWidth-et kérnek — így az egész sort elfoglalta, a nagy számnak
                // nulla hely maradt, és láthatatlanul, soronként egy karakterrel nyúlt
                // le a képernyő aljáig.
                Column(
                    Modifier.weight(1f),
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
 * Az étkezés színes bélyege: lekerekített négyzet az étkezés saját ikonjával, sarkában pipa,
 * ha már megette. A pipa körüli gyűrű a kártya színével rajzolódik, hogy a bélyeg
 * szélétől elváljon.
 */
@Composable
fun MealStamp(
    icon: ImageVector,
    color: Color,
    done: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
) {
    // A méret paraméter, mert az Étkezés részletei nagyobb bélyeget mutat. A belső
    // elemek is ehhez igazodnak, különben a nagyobb keretben ugyanakkora jel ülne.
    val badge = size * 0.38f
    Box(modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .clip(PlateShape.tile)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MealColors.contentOn(color),
                modifier = Modifier.size(size * 0.48f),
            )
        }
        if (done) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(badge)
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
                    modifier = Modifier.size(badge * 0.6f),
                )
            }
        }
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

/**
 * Szegmentált kapcsoló: fehér lapba ágyazott két-három pirula, a kijelölt zöld.
 * A Material FilterChip sorát váltja ki — az egymás mellé rakott önálló chipek
 * nem mutatják, hogy EGY döntés két állapotáról van szó.
 */
@Composable
fun <T> SegmentedToggle(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(PlateShape.tile)
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(PlateShape.innerButton)
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected = active, role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * Kerek jelölő a bevásárlólistához. A Material Checkbox szögletes és kicsi; itt a
 * kipipált tétel zöld korongot kap, a hátralévő üres gyűrűt — messziről is látszik,
 * mennyi van még hátra.
 */
@Composable
fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (checked) fill else Color.Transparent)
            .then(if (checked) Modifier else Modifier.border(2.dp, ring, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** Színes pont + ritkított nagybetűs csoportcím — bevásárlópolc, naprész. */
@Composable
fun GroupLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.size(9.dp))
        Text(
            text.uppercase(),
            style = MealLabelStyle.copy(fontSize = 11.5.sp),
            color = color,
        )
    }
}
