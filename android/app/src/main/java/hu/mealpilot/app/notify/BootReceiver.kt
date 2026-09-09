package hu.mealpilot.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Újraindítás, app-frissítés, idő- vagy időzónaváltás után a rendszer eldobja
 * a beállított ébresztéseket, ezért újra ki kell tenni őket.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                Notifications.ensureChannels(context)
                ReminderRefreshWorker.enqueuePeriodic(context)
                ReminderRefreshWorker.refreshNow(context)
            }
        }
    }
}
