package hu.mealpilot.app.data.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import hu.mealpilot.app.data.prefs.AiEffort
import hu.mealpilot.app.data.prefs.AiModel
import hu.mealpilot.app.data.prefs.AppSettings
import hu.mealpilot.app.data.prefs.SecureKeyStore
import hu.mealpilot.core.ai.ChatPrompts
import hu.mealpilot.core.ai.PlanPrompts
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Modellhívás a felhasználó SAJÁT Anthropic-kulcsával, a hivatalos Java SDK-n keresztül.
 *
 * Ez a fejlesztői és a saját használatú út. Fizető felhasználó nem fog API kulcsot
 * szerezni, ezért a bolti buildben a [BackendMealAi] a valódi kiszolgáló — ott a kulcs
 * a szerveren marad, és a kvótát sem a telefon számolja.
 */
class AnthropicMealAi(
    private val keyStore: SecureKeyStore,
    private val settingsProvider: suspend () -> AppSettings,
) : StreamingMealAi() {

    override val isConfigured: Boolean get() = keyStore.hasApiKey()

    @Volatile private var cachedClient: AnthropicClient? = null
    @Volatile private var cachedKey: String? = null

    private fun client(apiKey: String): AnthropicClient {
        cachedClient?.takeIf { cachedKey == apiKey }?.let { return it }
        return AnthropicOkHttpClient.builder().apiKey(apiKey).build().also {
            cachedClient = it
            cachedKey = apiKey
        }
    }

    /**
     * Egy hívás a Messages API-ra, streamelve. A streamelés itt nem kényelmi kérdés:
     * nagy max_tokens mellett a nem streamelt kérés a HTTP időkorlátba futna.
     */
    override suspend fun call(
        task: AiTask,
        userText: String,
        planDays: Int,
        chunkIndex: Int,
        isRetry: Boolean,
        onChars: (Int) -> Unit,
    ): String {
        val apiKey = keyStore.apiKey() ?: throw MissingApiKeyException()
        val settings = settingsProvider()

        val builder = MessageCreateParams.builder()
            .model(settings.model.id)
            .maxTokens(task.maxOutputTokens)
            .systemOfTextBlockParams(
                listOf(
                    TextBlockParam.builder()
                        .text(if (task == AiTask.CHAT) ChatPrompts.SYSTEM else PlanPrompts.SYSTEM)
                        // A rendszerprompt minden hívásnál azonos, ezért cache-elhető:
                        // a heti darabok és a javító körök után is olcsóbb lesz.
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()
                )
            )
            .addUserMessage(userText)

        // A Haiku 4.5 nem fogadja el az effort paramétert, ezért csak a nagyobb modelleknél adjuk meg.
        if (settings.model != AiModel.HAIKU) {
            builder.outputConfig(
                OutputConfig.builder().effort(settings.effort.toApiEffort()).build()
            )
        }

        val text = StringBuilder()
        client(apiKey).messages().createStreaming(builder.build()).use { response ->
            // Optional.stream() csak Java 9-től van; Androidon iterátorral és orElse(null)-lal
            // maradunk a Java 8-as felületen, amit a desugaring biztosan lefed.
            val events = response.stream().iterator()
            while (events.hasNext()) {
                val delta = events.next().contentBlockDelta().orElse(null) ?: continue
                val textDelta = delta.delta().text().orElse(null) ?: continue
                text.append(textDelta.text())
                onChars(text.length)
            }
        }
        coroutineContext.ensureActive()
        if (text.isBlank()) throw EmptyResponseException()
        return text.toString()
    }

    private fun AiEffort.toApiEffort(): OutputConfig.Effort = when (this) {
        AiEffort.LOW -> OutputConfig.Effort.LOW
        AiEffort.MEDIUM -> OutputConfig.Effort.MEDIUM
        AiEffort.HIGH -> OutputConfig.Effort.HIGH
    }

    override fun translate(error: Throwable): Throwable = when (error) {
        is CancellationException -> error
        is MealAiException -> error
        is UnauthorizedException -> MealAiException(
            "Az API kulcs érvénytelen vagy lejárt. Nézd meg a Beállításokban.", error
        )
        is RateLimitException -> MealAiException(
            "Túl sok kérés ment ki egymás után. Várj egy percet, aztán próbáld újra.", error
        )
        is BadRequestException -> MealAiException(
            "A kérést az API visszautasította: ${error.message ?: "ismeretlen ok"}", error
        )
        is AnthropicServiceException -> MealAiException(
            "Az AI szolgáltatás hibát adott (${error.errorType().map { it.toString() }.orElse("ismeretlen")}). " +
                "Próbáld újra kicsit később.",
            error,
        )
        is java.io.IOException -> MealAiException(
            "Nem sikerült elérni az AI szolgáltatást. Ellenőrizd az internetkapcsolatot.", error
        )
        else -> MealAiException(error.message ?: "Ismeretlen hiba a terv készítése közben.", error)
    }
}
