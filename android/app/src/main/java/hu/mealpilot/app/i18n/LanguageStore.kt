package hu.mealpilot.app.i18n

import android.content.Context
import android.content.res.Configuration
import hu.mealpilot.core.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Az app nyelve.
 *
 * Szándékosan sima SharedPreferences, nem DataStore: a nyelvet az Activity
 * `attachBaseContext` hívásában kell tudni, ami a felület felépítése ELŐTT fut, és ott
 * nincs hol megvárni egy felfüggesztett olvasást. Egy rossz sorrend itt azzal járna,
 * hogy az app egy pillanatra a régi nyelven villan fel.
 */
class LanguageStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(read())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    /** Igaz, ha a felhasználó már választott. Ebből tudjuk, kell-e nyelvválasztó. */
    val isChosen: Boolean get() = prefs.contains(KEY)

    fun current(): AppLanguage = _language.value

    fun set(language: AppLanguage) {
        prefs.edit().putString(KEY, language.tag).apply()
        _language.value = language
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        _language.value = read()
    }

    /**
     * Választás előtt a rendszer nyelvét javasoljuk — de csak azt, amit tudunk:
     * ismeretlen nyelvnél angol, mert az valószínűbb, hogy érti, mint a magyar.
     */
    private fun read(): AppLanguage {
        prefs.getString(KEY, null)?.let { return AppLanguage.fromTag(it) }
        return AppLanguage.fromSystemTag(Locale.getDefault().language)
    }

    companion object {
        private const val FILE = "mealpilot_language"
        private const val KEY = "language"

        /**
         * A megadott nyelvre állított kontextus. Az Activity ezzel indul, így a
         * rendszer által adott szövegek (dátumformátum, gombok) is stimmelnek.
         */
        fun wrap(base: Context): Context {
            val language = LanguageStore(base).current()
            val locale = Locale.forLanguageTag(language.tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        }
    }
}
