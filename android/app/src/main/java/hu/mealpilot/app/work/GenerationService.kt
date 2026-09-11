package hu.mealpilot.app.work

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import hu.mealpilot.app.MainActivity
import hu.mealpilot.app.R
import hu.mealpilot.app.appContainer
import hu.mealpilot.app.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Előtérben futó szolgáltatás a tervezés idejére.
 *
 * Nem azért kell, mert a munka nehéz, hanem mert a rendszer a háttérbe került appot
 * bármikor leállíthatja. Enélkül a felhasználó kilép, lezárja a képernyőt, és a félig
 * kész terv egyszerűen eltűnik. A tartós értesítés egyben a visszajelzés is: akkor is
 * látszik a haladás, ha közben mást csinál a telefonon.
 */
class GenerationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        startInForeground(notification("Étrend készül", "Indulás…", null))

        scope.launch {
            applicationContext.appContainer.generation.status.collect { status ->
                if (!status.running) {
                    stopSelf()
                    return@collect
                }
                val manager = androidx.core.app.NotificationManagerCompat.from(this@GenerationService)
                if (manager.areNotificationsEnabled()) {
                    runCatching {
                        manager.notify(
                            Notifications.ID_GENERATION,
                            notification(
                                title = status.headline.ifBlank { "Étrend készül" },
                                text = status.detail,
                                progress = status.fraction,
                            ),
                        )
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(notification: android.app.Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_GENERATION,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
        }.onFailure {
            // Ha a rendszer nem engedi az előtérbe lépést, a munka a háttérben akkor is fut
            // tovább — csak nincs rá garancia. Ez jobb, mint elszállni.
            Log.w(TAG, "Nem sikerült előtérbe lépni.", it)
        }
    }

    private fun notification(title: String, text: String, progress: Float?) =
        NotificationCompat.Builder(this, Notifications.CHANNEL_GENERATION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    Notifications.ID_GENERATION,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .apply {
                if (progress != null) {
                    setProgress(100, (progress * 100).toInt().coerceIn(0, 100), false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()

    companion object {
        private const val TAG = "GenerationService"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, GenerationService::class.java),
                )
            }.onFailure { Log.w(TAG, "A szolgáltatás indítása nem sikerült.", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GenerationService::class.java)) }
        }
    }
}
