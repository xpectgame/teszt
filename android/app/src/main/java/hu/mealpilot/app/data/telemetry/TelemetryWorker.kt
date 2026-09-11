package hu.mealpilot.app.data.telemetry

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import hu.mealpilot.app.appContainer
import java.util.concurrent.TimeUnit

/**
 * Feltölti az összegyűlt összeomlásokat és napi számlálókat.
 *
 * Háttérmunka, hálózatra várva: a felhasználó soha nem vár rá, és mobiladaton sem
 * kezd bele feleslegesen. Ha nincs mit küldeni, azonnal kilép.
 */
class TelemetryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val backend = container.backendClient ?: return Result.success()
        val telemetry = container.telemetry

        if (!container.settings.currentSettings().telemetryEnabled) {
            // Kikapcsolták: ami esetleg még bent maradt, azt eldobjuk, nem küldjük el.
            telemetry.clearAll()
            CrashReporter.clear(applicationContext)
            return Result.success()
        }

        val crashes = CrashReporter.pending(applicationContext)
        val snapshot = telemetry.snapshot()
        if (crashes.isEmpty() && snapshot.isEmpty) return Result.success()

        return try {
            backend.telemetry(
                day = snapshot.day,
                androidApi = CrashReporter.androidApi,
                device = CrashReporter.device,
                crashes = crashes,
                events = snapshot.counts,
            )
            if (crashes.isNotEmpty()) CrashReporter.clear(applicationContext)
            if (!snapshot.isEmpty) telemetry.clear(snapshot)
            Result.success()
        } catch (error: Exception) {
            // Nem baj, ha később megy el. A számlálókat NEM töröljük, amíg nem sikerült.
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "telemetry_upload_periodic"
        private const val ONE_SHOT_NAME = "telemetry_upload_now"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<TelemetryWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Indításkor hívjuk: ha az előző futás összeomlott, a jelentés így percek helyett
         * másodperceken belül megérkezik.
         */
        fun uploadNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<TelemetryWorker>().setConstraints(constraints).build(),
            )
        }
    }
}
