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

    /** Ennél több nem gyűlhet fel: egy hibaciklus különben tele írná a tárhelyet. */
    private const val MAX_FILES = 20

    /** A verem felső része elég a hiba megtalálásához; a mélye keretrendszeri zaj. */
    private const val MAX_FRAMES = 40
    private const val MAX_CAUSES = 5
    private const val MAX_CAUSE_FRAMES = 15

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
                .onFailure { Log.e(TAG, "A hiba mentése nem sikerült.", it) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
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
        appendLine("szál: ${thread.name}")
        appendLine(error.javaClass.name)
        error.stackTrace.take(MAX_FRAMES).forEach { frame ->
            appendLine("  ${frame.className}.${frame.methodName}:${frame.lineNumber}")
        }
        var cause = error.cause
        var depth = 0
        while (cause != null && depth < MAX_CAUSES) {
            appendLine("okozó: ${cause.javaClass.name}")
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
        val where = frame?.let { "${it.className}.${it.methodName}:${it.lineNumber}" } ?: "ismeretlen"
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
