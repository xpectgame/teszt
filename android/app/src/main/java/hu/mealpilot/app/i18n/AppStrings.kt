package hu.mealpilot.app.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import hu.mealpilot.core.i18n.AppLanguage
import java.util.Locale

/**
 * Szövegek a felhasználó választott nyelvén, felületen kívülről is.
 *
 * A Compose `stringResource` csak a felületen elérhető, az `Activity` kontextusából.
 * A háttérben futó részek — értesítések, munkák, hibaüzenetek — az alkalmazás
 * kontextusát látják, ami a RENDSZER nyelvét hordozza, nem a felhasználó választását.
 * Enélkül egy magyar rendszernyelvű telefonon az angolra kapcsolt app magyarul
 * értesítene és magyarul hibázna.
 *
 * A nyelvet függvényként kapja, nem értékként: a felhasználó menet közben is válthat,
 * és egy hosszú életű példány így nem ragad be a régi nyelvre.
 */
class AppStrings(
    context: Context,
    private val language: () -> AppLanguage,
) {

    private val appContext = context.applicationContext

    operator fun get(@StringRes resId: Int, vararg args: Any): String {
        val localized = localized()
        return if (args.isEmpty()) localized.getString(resId) else localized.getString(resId, *args)
    }

    private fun localized(): Context {
        val config = Configuration(appContext.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language().tag))
        return appContext.createConfigurationContext(config)
    }
}
