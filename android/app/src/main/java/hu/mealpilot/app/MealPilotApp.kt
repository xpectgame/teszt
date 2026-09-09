package hu.mealpilot.app

import android.app.Application
import hu.mealpilot.app.notify.Notifications
import hu.mealpilot.app.notify.ReminderRefreshWorker

class MealPilotApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannels(this)
        ReminderRefreshWorker.enqueuePeriodic(this)
    }
}

/** Kényelmi elérés a receiverekből és a Compose fáról. */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as MealPilotApp).container
