package hu.mealpilot.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Az Anthropic API kulcs tárolása. A kulcs a felhasználóé (saját fiók, saját számla),
 * ezért az eszközön marad, az Android Keystore-hoz kötött kulccsal titkosítva, és
 * ki van zárva a felhőmentésből (lásd res/xml/backup_rules.xml).
 */
class SecureKeyStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy { openPrefs() }

    /** Igaz, ha a titkosított tároló nem volt elérhető, és sima SharedPreferences-re esett vissza. */
    @Volatile
    var usingFallback: Boolean = false
        private set

    private fun openPrefs(): SharedPreferences = try {
        createEncrypted()
    } catch (first: Exception) {
        // A titkosított fájl megsérülhet (pl. Keystore-kulcs elveszik gyári visszaállításkor).
        // Ilyenkor egyszer újrapróbáljuk tiszta lappal.
        Log.w(TAG, "A titkosított tároló nem nyílt meg, újrapróbálom tisztán.", first)
        appContext.deleteSharedPreferences(FILE_NAME)
        try {
            createEncrypted()
        } catch (second: Exception) {
            Log.e(TAG, "A titkosított tároló nem elérhető, sima tárolóra váltok.", second)
            usingFallback = true
            appContext.getSharedPreferences(FALLBACK_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun createEncrypted(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun apiKey(): String? = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }

    fun setApiKey(value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_API) else putString(KEY_API, value.trim())
        }.apply()
    }

    fun hasApiKey(): Boolean = apiKey() != null

    /** Csak a kulcs eleje és vége, hogy a beállításokban azonosítható legyen. */
    fun maskedApiKey(): String? = apiKey()?.let {
        if (it.length <= 12) "••••" else "${it.take(8)}…${it.takeLast(4)}"
    }

    private companion object {
        const val TAG = "SecureKeyStore"
        const val FILE_NAME = "mealpilot_secure_prefs"
        const val FALLBACK_FILE_NAME = "mealpilot_prefs_plain"
        const val KEY_API = "anthropic_api_key"
    }
}
