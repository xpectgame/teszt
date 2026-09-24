package hu.mealpilot.app.data.telemetry

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Összeomlás-jelentés külső szolgáltató nélkül.
 *
 * A hibát NEM próbáljuk meg a helyszínen elküldeni: a folyamat éppen haldoklik, a
 * hálózat pedig lassú — a jelentés fele úton veszne el. Ezért fájlba írjuk, és a
 * következő indításkor töltjük fel. A régi kezelőt utána meghívjuk, hogy a rendszer
 * a szokásos módon zárja le az appot, és a Play Console-ban is látszódjon a hiba.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val DIR = "crashes"

    /**
     * A kikapcsolás TÜKRE, sima SharedPreferences-ben.
     *
     * Az adatkezelési tájékoztató (3a. pont) és a támogatási oldal is azt ígéri, hogy
     * kikapcsolva „az alkalmazás nem is gyűjti ezeket, nem csak a küldést hagyja el".
     * A napi számlálóknál ez állt is: a `Telemetry.record` megnézi a kapcsolót. Az
     * összeomlás-jelentés viszont a kapcsolótól FÜGGETLENÜL fájlba írt, és csak a
     * napi háttérmunka dobta el — ha egyáltalán lefutott (backend nélküli buildben
     * soha).
     *
     * Miért nem a DataStore-ból olvassuk: a kezelő akkor fut, amikor a folyamat éppen
     * haldoklik. Ott nincs hol megvárni egy felfüggesztett olvasást, és egy korutin
     * elindítására sincs garancia. Ez a tükör szinkron olvasható, és túléli az
     * újraindítást is — ugyanaz a megfontolás, mint a [hu.mealpilot.app.i18n.LanguageStore]-nál.
     */
    private const val SETTING_FILE = "mealpilot_crash_reporting"
    private const val SETTING_KEY = "enabled"

    /** Ennél több nem gyűlhet fel: egy hibaciklus különben tele írná a tárhelyet. */
    private const val MAX_FILES = 20

    /** A verem felső része elég a hiba megtalálásához; a mélye keretrendszeri zaj. */
    private const val MAX_FRAMES = 40
    private const val MAX_CAUSES = 5
    private const val MAX_CAUSE_FRAMES = 15

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * A kapcsoló állásának átvezetése. A beállítás a DataStore-ban él; ide azért kerül
     * át, hogy az összeomláskezelő szinkron el tudja olvasni.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext
            .getSharedPreferences(SETTING_FILE, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SETTING_KEY, true) == enabled) return
        prefs.edit().putBoolean(SETTING_KEY, enabled).apply()
        // A kikapcsolás a MÁR összegyűlt jelentésekre is vonatkozik. Aki most kapcsolta
        // ki, az most akar csendet, nem a következő háttérmunka után.
        if (!enabled) clear(context)
    }

    /** Alapértéke igaz — ugyanaz, mint az `AppSettings.telemetryEnabled`-é. */
    fun isEnabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(SETTING_FILE, Context.MODE_PRIVATE)
        .getBoolean(SETTING_KEY, true)

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
                .onFailure { Log.e(TAG, "A hiba mentése nem sikerült.", it) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * `internal`, nem `private`: a modul tesztjeinek látniuk kell. Az a kérdés, hogy a
     * kikapcsolt kapcsoló mellett TÉNYLEG nem születik-e fájl — ezt egy tesztbe
     * bemásolt változaton nem lehet megmérni.
     */
    internal fun write(context: Context, thread: Thread, error: Throwable) {
        // Kikapcsolva NEM GYŰJTÜNK, nem csak nem küldünk — ezt a tájékoztató szó
        // szerint így ígéri.
        if (!isEnabled(context)) return
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        if ((dir.listFiles()?.size ?: 0) >= MAX_FILES) return

        val record = CrashRecord(
            exception = error.javaClass.name,
            stack = renderStack(thread, error),
            fingerprint = fingerprint(error),
            happenedAt = System.currentTimeMillis(),
        )
        File(dir, "${UUID.randomUUID()}.json")
            .writeText(json.encodeToString(CrashRecord.serializer(), record))
    }

    /**
     * A hiba HELYE, és semmi más.
     *
     * Az adatkezelési tájékoztató azt ígéri, hogy „a hiba helyét" küldjük el, és hogy
     * étrend, napló és testadat nem megy el. A `printStackTrace` ezt nem tartaná be: a
     * kimenet a kivétel és MINDEN okozója `toString()`-jével kezdődik, az pedig szabad
     * szöveg. Egy `NumberFormatException: For input string: "78,5"` így a beírt
     * testsúlyt vinné magával.
     *
     * Ma nincs ilyen út — a szám-értelmezés mindenhol őrzött —, de ez a garanciát nem
     * pótolja: egy kivételüzenet bárhonnan kaphat felhasználói szöveget, és egy jogi
     * ígéret nem támaszkodhat arra, hogy éppen egyik sem teszi.
     *
     * Ezért a vermet a keretekből építjük: osztály, függvény, sor. Az okozókat is csak
     * az OSZTÁLYUK nevével soroljuk fel. A hiba megtalálásához ez elég; a szöveg nem
     * nekünk szólt.
     */
    internal fun renderStack(thread: Thread, error: Throwable): String = buildString {
        // A címkék angolul: ez GÉPI jelentésformátum, nem felületi szöveg. Így a
        // beégetett-magyar-szöveg ellenőrzés sem kap rá kivételt.
        appendLine("thread: ${thread.name}")
        appendLine(error.javaClass.name)
        error.stackTrace.take(MAX_FRAMES).forEach { frame ->
            appendLine("  ${frame.className}.${frame.methodName}:${frame.lineNumber}")
        }
        var cause = error.cause
        var depth = 0
        while (cause != null && depth < MAX_CAUSES) {
            appendLine("caused by: ${cause.javaClass.name}")
            cause.stackTrace.take(MAX_CAUSE_FRAMES).forEach { frame ->
                appendLine("  ${frame.className}.${frame.methodName}:${frame.lineNumber}")
            }
            cause = cause.cause
            depth++
        }
    }.trimEnd()

    /**
     * Az azonos hibák közös ujjlenyomata: a kivétel osztálya és az első SAJÁT
     * kódsor. A keretrendszer belső hívásai nélkül ugyanaz a hiba nem esik szét
     * tucatnyi különböző bejegyzésre.
     */
    private fun fingerprint(error: Throwable): String {
        val frame = error.stackTrace.firstOrNull { it.className.startsWith("hu.mealpilot") }
            ?: error.stackTrace.firstOrNull()
        // Angolul, mert nem a felhasználónak szól: egy `hu.mealpilot.app.Foo.bar:12`
        // alakú belső azonosító mellett áll, és ugyanígy nem fordítjuk.
        val where = frame?.let { "${it.className}.${it.methodName}:${it.lineNumber}" } ?: "unknown"
        return "${error.javaClass.simpleName}@$where".take(64)
    }

    fun pending(context: Context): List<CrashRecord> {
        val dir = File(context.filesDir, DIR)
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching { json.decodeFromString(CrashRecord.serializer(), file.readText()) }.getOrNull()
        }
    }

    fun clear(context: Context) {
        File(context.filesDir, DIR).listFiles()?.forEach { it.delete() }
    }

    /** Az adattörléshez: a tükör is a felhasználó beállítása. */
    fun clearAll(context: Context) {
        clear(context)
        context.applicationContext
            .getSharedPreferences(SETTING_FILE, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    val device: String get() = "${Build.MANUFACTURER} ${Build.MODEL}"
    val androidApi: Int get() = Build.VERSION.SDK_INT
}

/**
 * Egy elmentett összeomlás.
 *
 * A kivétel ÜZENETE szándékosan nincs benne: szabad szöveg, ami felhasználói adatot
 * hordozhat, és az adatkezelési tájékoztató szerint az nem hagyja el a készüléket.
 * A [stack] csak osztály-, függvény- és sorneveket tartalmaz.
 */
@Serializable
data class CrashRecord(
    val exception: String,
    val stack: String,
    val fingerprint: String,
    val happenedAt: Long,
)
