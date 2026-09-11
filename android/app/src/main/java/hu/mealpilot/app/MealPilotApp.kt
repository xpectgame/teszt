package hu.mealpilot.app

import android.app.Application
import hu.mealpilot.app.data.telemetry.CrashReporter
import hu.mealpilot.app.data.telemetry.TelemetryEvent
import hu.mealpilot.app.data.telemetry.TelemetryWorker
import hu.mealpilot.app.notify.Notifications
import hu.mealpilot.app.notify.ReminderRefreshWorker

class MealPilotApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // A hibakezelő MINDEN más előtt: ha maga az indulás omlik össze, arról is
        // szeretnénk jelentést kapni.
        CrashReporter.install(this)

        container = AppContainer(this)
        Notifications.ensureChannels(this)
        ReminderRefreshWorker.enqueuePeriodic(this)

        container.telemetry.record(TelemetryEvent.APP_OPEN)
        TelemetryWorker.enqueuePeriodic(this)
        // Indításkor is próbálkozunk: ha az előző futás összeomlott, a jelentés így
        // nem vár egy napot.
        TelemetryWorker.uploadNow(this)
    }
}

/** Kényelmi elérés a receiverekből és a Compose fáról. */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as MealPilotApp).container
