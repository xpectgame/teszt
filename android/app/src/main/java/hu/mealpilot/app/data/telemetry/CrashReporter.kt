package hu.mealpilot.app.data.telemetry

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
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

        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { error.printStackTrace(it) }
        }.toString()

        val record = CrashRecord(
            exception = error.javaClass.name,
            message = error.message?.take(1_000),
            stack = "szál: ${thread.name}\n$stack",
            fingerprint = fingerprint(error),
            happenedAt = System.currentTimeMillis(),
        )
        File(dir, "${UUID.randomUUID()}.json")
            .writeText(json.encodeToString(CrashRecord.serializer(), record))
    }

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

@Serializable
data class CrashRecord(
    val exception: String,
    val message: String? = null,
    val stack: String,
    val fingerprint: String,
    val happenedAt: Long,
)
