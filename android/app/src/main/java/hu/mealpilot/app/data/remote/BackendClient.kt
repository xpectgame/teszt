package hu.mealpilot.app.data.remote

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
    fun generate(
        task: String,
        prompt: String,
        days: Int,
        chunkIndex: Int,
        isRetry: Boolean,
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
            ),
        )

        execute(post("v1/generate", body)).use { response ->
            checkOk(response)
            val source = response.body?.source() ?: throw MealAiException("A szolgáltatás üres választ adott.")
            val text = StringBuilder()
            var sawDone = false

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val event = runCatching { json.decodeFromString(StreamEvent.serializer(), line) }.getOrNull()
                    ?: continue

                when (event.type) {
                    "delta" -> event.text?.let {
                        text.append(it)
                        onChars(text.length)
                    }

                    "error" -> throw MealAiException(
                        event.message ?: "A tervező szolgáltatás hibát adott."
                    )

                    "done" -> sawDone = true
                }
            }

            // Megszakadt kapcsolatnál a fél válasz értelmezhetetlen JSON lenne, és a
            // felhasználó egy zavaros elemzési hibát látna a valódi ok helyett.
            if (!sawDone) throw MealAiException(
                "A kapcsolat megszakadt a terv készítése közben. Próbáld újra."
            )
            if (text.isBlank()) throw MealAiException("A szolgáltatás üres választ küldött. Próbáld újra.")
            return text.toString()
        }
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
                        message = it.message,
                        stack = it.stack,
                        fingerprint = it.fingerprint,
                        happenedAt = it.happenedAt,
                    )
                },
                events = events,
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
            "Nem sikerült elérni a szolgáltatást. Ellenőrizd az internetkapcsolatot.",
            error,
        )
    }

    private fun checkOk(response: Response) {
        if (response.isSuccessful) return
        val raw = response.body?.string().orEmpty()
        val error = runCatching { json.decodeFromString(ErrorResponse.serializer(), raw) }.getOrNull()

        when (response.code) {
            402 -> throw QuotaExceededException(
                code = error?.error ?: "QUOTA",
                message = error?.message ?: "Elfogyott a havi keret.",
                upgradeOffered = error?.upgrade ?: true,
            )

            401 -> throw MealAiException(
                "A szolgáltatás nem ismerte fel ezt a telepítést. Indítsd újra az appot."
            )

            413 -> throw MealAiException("A kérés túl hosszú lett. Rövidítsd a megjegyzéseidet.")

            429 -> throw MealAiException(
                "Most sok kérés fut egyszerre. Várj egy percet, aztán próbáld újra."
            )

            in 500..599 -> throw MealAiException(
                "A tervező szolgáltatás éppen nem elérhető. Próbáld újra kicsit később."
            )

            else -> throw MealAiException(
                error?.message ?: "A szolgáltatás visszautasította a kérést (${response.code})."
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
    @SerialName("ai_plans_per_month") val aiPlansPerMonth: Int = 1,
    @SerialName("chat_messages_per_month") val chatMessagesPerMonth: Int = 10,
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
