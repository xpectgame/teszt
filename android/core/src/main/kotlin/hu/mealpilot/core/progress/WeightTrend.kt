package hu.mealpilot.core.progress

import kotlin.math.abs
import kotlin.math.roundToInt

/** Egy nap a haladásból: a mért súly (ha volt mérés aznap) és a simított trend. */
data class TrendPoint(
    val epochDay: Long,
    /** Null, ha aznap nem volt mérés. A trend ilyenkor is értelmes. */
    val measuredKg: Double?,
    val trendKg: Double,
)

/**
 * Trendsúly: a napi ingadozás alól kilátszó valódi irány.
 *
 * MIÉRT KELL. A testsúly napról napra 1–2 kg-ot ugrál víztől, sótól, bélrendszertől —
 * a heti fél kilós fogyás ebben a zajban LÁTHATATLAN. Aki reggel 0,4 kg-mal többet mér
 * a tegnapinál, azt hiszi, elrontotta, pedig lehet, hogy pont jól halad. A nyers súly
 * kirajzolva ezért nem tájékoztat, hanem félrevezet.
 *
 * A simítás a Hacker's Diet régi, bevált módszere: minden mérésnél a trend a mért érték
 * felé mozdul a különbség [SMOOTHING] részével. Lassan követ, cserébe nem ugrál.
 *
 * MIÉRT NAPTÁRI NAPOKBAN SZÁMOL, ÉS NEM MÉRÉSENKÉNT. Ha valaki két hétig nem mér, majd
 * mér egyet, a mérésenkénti simítás úgy kezelné, mintha az másnap lett volna — a trend
 * két hét alatt egy lépést mozdulna. Itt a kihagyott napokon a trend egyszerűen áll:
 * nincs új információ, tehát nem is állítunk semmit.
 */
object WeightTrend {

    /**
     * Mennyit mozdul a trend egy méréstől. 0,1-nél a trend nagyjából tíz nap alatt
     * követi le a valódi elmozdulást — elég lassú ahhoz, hogy a napi zaj ne rángassa,
     * és elég gyors ahhoz, hogy egy valódi fordulat egy héten belül látszódjon.
     */
    const val SMOOTHING = 0.1

    /** Ennyi napra visszatekintve számolunk heti ütemet. */
    const val RATE_WINDOW_DAYS = 28

    /**
     * A grafikon legkisebb függőleges ablaka kilóban.
     *
     * Egy-két mérés között gyakran tized kiló a különbség, a SIMÍTOTT trend pedig még
     * ennél is laposabb — szándékosan. Ha az ablakot pontosan az adatokra szabnánk, egy
     * 5 dekás ingadozás hegyvidéknek látszana.
     */
    const val MIN_CHART_SPAN_KG = 1.0

    /**
     * A grafikon függőleges ablaka: (alsó él, felső él).
     *
     * Nem elég az ablakot felnagyítani, KÖZÉPRE is kell tenni az adatokat. Korábban a
     * kód csak a „legalább 1 kg" osztót cserélte ki, a nullpont maradt a legkisebb
     * mért érték — így a lapos szakaszok nem középen futottak, hanem a grafikon ALJÁRA
     * ragadva. A kezdeti napokban (amikor a trend a legsimább) ez volt a tipikus kép.
     */
    fun chartWindow(
        values: List<Double>,
        minSpanKg: Double = MIN_CHART_SPAN_KG,
    ): Pair<Double, Double> {
        if (values.isEmpty()) return 0.0 to minSpanKg
        val low = values.min()
        val high = values.max()
        if (high - low >= minSpanKg) return low to high
        val middle = (low + high) / 2.0
        return middle - minSpanKg / 2.0 to middle + minSpanKg / 2.0
    }

    /**
     * Napi sorozat az első méréstől az utolsóig.
     *
     * A bemenet (nap, kg) párok, tetszőleges sorrendben; egy napra több mérés esetén
     * az utolsó számít. Üres bemenetre üres lista.
     */
    fun series(measurements: List<Pair<Long, Double>>): List<TrendPoint> {
        if (measurements.isEmpty()) return emptyList()
        val byDay = measurements.sortedBy { it.first }.associate { it.first to it.second }
        val first = byDay.keys.min()
        val last = byDay.keys.max()

        var trend = byDay.getValue(first)
        val out = mutableListOf<TrendPoint>()
        for (day in first..last) {
            val measured = byDay[day]
            if (measured != null) trend += (measured - trend) * SMOOTHING
            out += TrendPoint(epochDay = day, measuredKg = measured, trendKg = trend)
        }
        return out
    }

    /**
     * Heti változás kg-ban a trend alapján. Negatív = fogyás.
     *
     * Null, ha nincs elég idő mögötte: két nap különbségéből heti ütemet számolni
     * magabiztos hazugság lenne. Legalább [minimumDays] napnyi sorozat kell.
     */
    fun weeklyChangeKg(
        points: List<TrendPoint>,
        windowDays: Int = RATE_WINDOW_DAYS,
        minimumDays: Int = 10,
    ): Double? {
        if (points.size < minimumDays) return null
        val window = points.takeLast(windowDays)
        if (window.size < minimumDays) return null
        val days = window.last().epochDay - window.first().epochDay
        if (days <= 0) return null
        return (window.last().trendKg - window.first().trendKg) / days * 7.0
    }

    /**
     * Hány nap múlva éri el a célsúlyt a MÉRT ütemmel.
     *
     * Szándékosan a mérttel, nem a tervezettel: a tervezett ütem egy szándék, a mért
     * az, ami történik. A kettő eltérése a leghasznosabb információ, amit ez a
     * képernyő adhat.
     *
     * Null, ha nincs mért ütem, ha az ütem rossz irányba mutat, ha a cél már
     * teljesült, vagy ha a becslés két évnél messzebbre esne — ilyenkor a dátum nem
     * óvatos becslés lenne, hanem kitaláció.
     */
    fun daysToTargetAtMeasuredRate(
        points: List<TrendPoint>,
        targetKg: Double,
        windowDays: Int = RATE_WINDOW_DAYS,
    ): Int? {
        val weekly = weeklyChangeKg(points, windowDays) ?: return null
        val current = points.lastOrNull()?.trendKg ?: return null
        val remaining = current - targetKg
        if (abs(remaining) < 0.1) return null
        // Ugyanabba az irányba kell mutatnia, amerre a cél van.
        if (remaining > 0 && weekly >= -0.01) return null
        if (remaining < 0 && weekly <= 0.01) return null
        // A heti ütem fogyásnál NEGATÍV, a hátralévő út fogyásnál POZITÍV: az
        // osztásnál ezért kell az előjelváltás, különben a becslés a múltba mutat.
        val days = remaining / -weekly * 7.0
        if (days <= 0 || days > MAX_HORIZON_DAYS) return null
        return days.roundToInt()
    }

    /**
     * Két év fölött a becslés nem tájékoztat, csak elveszi a kedvet — és annyi idő
     * alatt úgyis megváltozik minden, amiből számoltuk.
     */
    private const val MAX_HORIZON_DAYS = 730.0
}
