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
 *
 * A kizárás MINDKÉT tárolóra vonatkozik: a titkosítottra és a [FALLBACK_FILE_NAME]
 * tartalékra is. Utóbbi a fontosabb — az tartja a kulcsot olvashatóan —, és sokáig
 * pont az maradt ki. A `tools/check-backup-rules.py` azóta összeméri a két listát.
 */
class SecureKeyStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy { openPrefs() }

    @Volatile
    private var fellBack: Boolean = false

    /**
     * Igaz, ha a titkosított tároló nem volt elérhető, és sima SharedPreferences-re
     * esett vissza.
     *
     * A lekérdezés MEGNYITJA a tárolót, ha még nem volt nyitva. E nélkül a beállítások
     * képernyője hamis nyugalmat mutathatott: a jelzőt a visszaesés állítja be, az
     * viszont csak a tároló első használatakor derül ki — aki előbb kérdezte meg,
     * mindig „rendben"-t kapott.
     */
    val usingFallback: Boolean
        get() {
            prefs
            return fellBack
        }

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
            fellBack = true
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

    /**
     * A telepítés azonosítója a saját backend felé.
     *
     * Nem fiók és nem személyes adat: egy véletlen szám, ami az első indításkor
     * keletkezik, és csak arra jó, hogy a havi keretet valamihez kösse a szerver.
     * Törléskor (Minden adat törlése, app eltávolítása) eltűnik, és újat kap — az
     * előfizetés ettől nem vész el, mert az a Google fiókhoz tartozik.
     */
    fun installId(): String {
        prefs.getString(KEY_INSTALL, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val bytes = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }
        val generated = android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        prefs.edit().putString(KEY_INSTALL, generated).apply()
        return generated
    }

    /**
     * Mindent töröl: az API kulcsot ÉS a telepítési azonosítót.
     *
     * A „Minden adat törlése" eddig csak a kulcsot vette le. A telepítési azonosító
     * — az EGYETLEN dolog, ami ezt a készüléket a szerverhez köti — ott maradt, pedig
     * az [installId] leírása azt ígéri, hogy eltűnik és újat kap.
     *
     * Nem csak ígéret kérdése. A helyi kvótaszámlálók a törléssel nullázódnak, tehát
     * az app három ingyenes tervet mutatott — a szerver viszont a RÉGI azonosítót
     * látta, és minden kérést elutasított. A felhasználó mást látott, mint amit kapott.
     *
     * A próbakeret így újraindítható. Ez nem új rés: ugyanez megy az app
     * eltávolításával vagy az Android „Adatok törlése" gombjával is, és a backend
     * `limits.ts` ki is mondja, hogy a valódi felső korlát a közös napi mennyezet,
     * nem a telepítésenkénti számláló.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val TAG = "SecureKeyStore"
        const val FILE_NAME = "mealpilot_secure_prefs"
        const val FALLBACK_FILE_NAME = "mealpilot_prefs_plain"
        const val KEY_API = "anthropic_api_key"
        const val KEY_INSTALL = "install_id"
    }
}
