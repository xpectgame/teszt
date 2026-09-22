package hu.mealpilot.app.data.remote

import hu.mealpilot.app.R
import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.app.data.ai.MealAiException
import hu.mealpilot.app.data.telemetry.CrashRecord
import hu.mealpilot.app.data.ai.QuotaExceededException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A saját backend HTTP kliense.
 *
 * A backend azért van, mert egy fizető felhasználó nem fog Anthropic API kulcsot
 * szerezni — és mert a jogosultságról nem dönthet a telefon. Itt csak a kérés megy ki:
 * a kulcs, a rendszerprompt, a kvóta és az előfizetés-ellenőrzés mind a szerveren van.
 */
class BackendClient(
    private val baseUrl: String,
    private val installId: () -> String,
    /** A Play vásárlási tokenje, ha van előfizetés. Ebből igazolja a szerver a jogosultságot. */
    private val purchaseToken: () -> String?,
    private val appVersion: String,
    /**
     * A fejlesztő saját buildjének kulcsa, ha ez egy ilyen build. A szerver ettől kvóta
     * nélkül szolgál ki. A boltból telepített appban üres.
     */
    private val ownerKey: String = "",
    private val strings: AppStrings,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // Egy tervszakasz percekig streamelhet; az olvasási időkorlát a darabok
            // közti csendre vonatkozik, nem a teljes hívásra.
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    /**
     * Lefuttat egy hívást, és a beérkező szöveget összefűzi.
     *
     * @param onChars minden beérkezett darab után az eddigi karakterszámmal hívódik —
     *   ebből lesz a látható haladás a felületen.
     */
    suspend fun generate(
        task: String,
        prompt: String,
        days: Int,
        chunkIndex: Int,
        isRetry: Boolean,
        /**
         * A terv nyelve. A szerver ebből választ rendszerpromptot — ez az EGYETLEN
         * dolog, amit a kliens a prompton befolyásolhat, és az is csak választás:
         * vagy a magyar, vagy az angol szöveget kapja.
         */
        language: String,
        onChars: (Int) -> Unit,
    ): String {
        val body = json.encodeToString(
            GenerateRequest.serializer(),
            GenerateRequest(
                task = task,
                prompt = prompt,
                days = days,
                chunkIndex = chunkIndex,
                isRetry = isRetry,
                language = language,
            ),
        )

        val call = client.newCall(post("v1/generate", body))

        // A megszakítás a KAPCSOLATOT is bontja.
        //
        // A „Mégsem" eddig csak a felületet állította meg. Az olvasó ciklus blokkoló
        // hívás egy nem felfüggeszthető metódusban: a korutin megszakítása nem
        // szakította félbe, tehát a válasz a végéig befolyt. A szerver ebből nem vette
        // észre, hogy a kliens elment — pedig a kapcsolat bontása az EGYETLEN pont,
        // ahol egy megszakított terv költsége tényleg megáll (lásd `backend/src/index.ts`,
        // az `upstream` megszakító). A felhasználó lemondta, a számla megjött.
        val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }

        try {
            val response = try {
                call.execute()
            } catch (error: IOException) {
                // Egy megszakítás miatt bontott hívás IOException-nel jön vissza, de az
                // nem hálózati hiba: ott a megszakítás a helyes kimenet.
                currentCoroutineContext().ensureActive()
                throw MealAiException(strings[R.string.error_no_network_service], error)
            }
            response.use {
                checkOk(response)
                val source = response.body?.source()
                    ?: throw MealAiException(strings[R.string.error_empty_service_response])
                return readStream(source, onChars)
            }
        } finally {
            cancelHandle.dispose()
        }
    }

    /**
     * Az NDJSON folyam összeolvasása.
     *
     * Külön metódus, hogy a megszakítás viselkedése hálózat nélkül is mérhető legyen:
     * a lényeg a ciklus elején álló [ensureActive], ami soronként megnézi, akarja-e
     * még valaki ezt a választ.
     */
    internal suspend fun readStream(source: BufferedSource, onChars: (Int) -> Unit): String {
        val text = StringBuilder()
        var sawDone = false

        while (!source.exhausted()) {
            // Soronként megkérdezzük, kell-e még. Enélkül a lemondott terv a végéig
            // befolyt, és csak utána derült ki, hogy senki nem várja.
            currentCoroutineContext().ensureActive()
            val line = source.readUtf8Line() ?: break
            if (line.isBlank()) continue
            val event = runCatching { json.decodeFromString(StreamEvent.serializer(), line) }.getOrNull()
                ?: continue

            when (event.type) {
                "delta" -> event.text?.let {
                    text.append(it)
                    onChars(text.length)
                }

                // A KÓDBÓL választunk szöveget, nem a szerver mondatából: a
                // szerver üzenetei csak magyarul léteznek.
                "error" -> throw MealAiException(
                    when (event.code) {
                        "RATE_LIMIT" -> strings[R.string.error_busy]
                        else -> strings[R.string.error_planner_service]
                    }
                )

                "done" -> sawDone = true
            }
        }

        // Megszakadt kapcsolatnál a fél válasz értelmezhetetlen JSON lenne, és a
        // felhasználó egy zavaros elemzési hibát látna a valódi ok helyett.
        if (!sawDone) throw MealAiException(strings[R.string.error_connection_lost])
        if (text.isBlank()) throw MealAiException(strings[R.string.error_empty_service_retry])
        return text.toString()
    }

    /** Jogosultság és a hónapból hátralévő keret — a szerver az igazság forrása. */
    fun session(): BackendSession {
        execute(post("v1/session", "{}")).use { response ->
            checkOk(response)
            val raw = response.body?.string().orEmpty()
            return json.decodeFromString(BackendSession.serializer(), raw)
        }
    }

    /** Összeomlások és napi számlálók feltöltése. */
    fun telemetry(
        day: String,
        androidApi: Int,
        device: String,
        crashes: List<CrashRecord>,
        events: Map<String, Int>,
        firstToday: Boolean,
    ) {
        val body = json.encodeToString(
            TelemetryRequest.serializer(),
            TelemetryRequest(
                day = day,
                androidApi = androidApi,
                device = device,
                crashes = crashes.map {
                    TelemetryCrash(
                        exception = it.exception,
                        // Üzenet nem megy: szabad szöveg, felhasználói adatot hordozhat.
                        stack = it.stack,
                        fingerprint = it.fingerprint,
                        happenedAt = it.happenedAt,
                    )
                },
                events = events,
                firstToday = firstToday,
            ),
        )
        execute(post("v1/telemetry", body)).use { response -> checkOk(response) }
    }

    /** „Jelentsd ezt a tervet" bejelentés elküldése. */
    fun report(kind: String, reason: String, detail: String?, payload: String?) {
        val body = json.encodeToString(
            ReportRequest.serializer(),
            ReportRequest(kind = kind, reason = reason, detail = detail, payload = payload),
        )
        execute(post("v1/report", body)).use { response -> checkOk(response) }
    }

    private fun post(path: String, body: String): Request {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/" + path)
            .addHeader("authorization", "Bearer ${installId()}")
            .addHeader("x-app-version", appVersion)
            .post(body.toRequestBody(JSON_MEDIA))
        purchaseToken()?.takeIf { it.isNotBlank() }?.let { builder.addHeader("x-play-purchase-token", it) }
        ownerKey.takeIf { it.isNotBlank() }?.let { builder.addHeader("x-owner-key", it) }
        return builder.build()
    }

    private fun execute(request: Request): Response = try {
        client.newCall(request).execute()
    } catch (error: IOException) {
        throw MealAiException(
            strings[R.string.error_no_network_service],
            error,
        )
    }

    /**
     * A kvótahiba szövege a KÓDBÓL, nem a szerver mondatából.
     *
     * A szerver üzenetei csak magyarul léteznek — nem is kaphat nyelvet minden
     * végponton. A fizetőfal viszont a legfontosabb szöveg az appban: ott kérünk
     * pénzt. Egy angol felhasználó eddig magyarul kapta meg, hogy elfogyott a kerete,
     * pont abban a pillanatban.
     *
     * A kód a szerződés a két oldal között, a szöveg nem. Ismeretlen kódnál marad az
     * általános szöveg — az is a felhasználó nyelvén.
     */
    private fun quotaMessage(code: String?): String = when (code) {
        "PLAN_QUOTA" -> strings[R.string.error_quota_plans]
        "MESSAGE_QUOTA" -> strings[R.string.error_quota_messages]
        "PREMIUM_ONLY" -> strings[R.string.error_premium_only]
        "PLAN_TOO_LONG" -> strings[R.string.error_plan_too_long]
        "TOKEN_CAP" -> strings[R.string.error_token_cap]
        else -> strings[R.string.error_quota_exhausted]
    }

    private fun checkOk(response: Response) {
        if (response.isSuccessful) return
        val raw = response.body?.string().orEmpty()
        val error = runCatching { json.decodeFromString(ErrorResponse.serializer(), raw) }.getOrNull()

        when (response.code) {
            402 -> throw QuotaExceededException(
                code = error?.error ?: "QUOTA",
                message = quotaMessage(error?.error),
                upgradeOffered = error?.upgrade ?: true,
            )

            401 -> throw MealAiException(
                strings[R.string.error_unknown_install]
            )

            413 -> throw MealAiException(strings[R.string.error_request_too_long])

            429 -> throw MealAiException(
                strings[R.string.error_busy]
            )

            in 500..599 -> throw MealAiException(
                strings[R.string.error_service_down]
            )

            // A szerver szövege itt fejlesztőnek szól („Ismeretlen feladat."), és csak
            // magyarul létezik. A felhasználónak a HTTP kód többet mond, a saját nyelvén.
            else -> throw MealAiException(
                strings[R.string.error_service_refused, response.code]
            )
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class GenerateRequest(
    val task: String,
    val prompt: String,
    val days: Int,
    @SerialName("chunk_index") val chunkIndex: Int,
    @SerialName("is_retry") val isRetry: Boolean,
    val language: String,
)

@Serializable
private data class StreamEvent(
    val type: String,
    val text: String? = null,
    val message: String? = null,
    val code: String? = null,
)

@Serializable
private data class ErrorResponse(
    val error: String? = null,
    val message: String? = null,
    val upgrade: Boolean? = null,
)

@Serializable
private data class TelemetryRequest(
    val day: String,
    @SerialName("android_api") val androidApi: Int,
    val device: String,
    val crashes: List<TelemetryCrash>,
    val events: Map<String, Int>,
    @SerialName("first_today") val firstToday: Boolean = true,
)

@Serializable
private data class TelemetryCrash(
    val exception: String,
    val message: String? = null,
    val stack: String,
    val fingerprint: String,
    @SerialName("happened_at") val happenedAt: Long,
)

@Serializable
private data class ReportRequest(
    val kind: String,
    val reason: String,
    val detail: String? = null,
    val payload: String? = null,
)

/** A szerver által számolt jogosultság. A felület ebből írja ki, mennyi keret maradt. */
@Serializable
data class BackendSession(
    val tier: String = "FREE",
    @SerialName("subscription_state") val subscriptionState: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val period: String = "",
    val limits: BackendLimits = BackendLimits(),
    val usage: BackendUsage = BackendUsage(),
    val remaining: BackendRemaining = BackendRemaining(),
)

@Serializable
data class BackendLimits(
    @SerialName("ai_plans") val aiPlans: Int = 3,
    @SerialName("chat_messages") val chatMessages: Int = 20,
    @SerialName("max_plan_days") val maxPlanDays: Int = 3,
    @SerialName("can_refine_days") val canRefineDays: Boolean = false,
)

@Serializable
data class BackendUsage(
    val plans: Int = 0,
    val messages: Int = 0,
)

@Serializable
data class BackendRemaining(
    val plans: Int = 0,
    val messages: Int = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
)
