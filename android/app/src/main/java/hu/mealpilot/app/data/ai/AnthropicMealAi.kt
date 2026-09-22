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
import hu.mealpilot.app.R
import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.core.i18n.AppLanguage
import hu.mealpilot.app.data.prefs.SecureKeyStore
import hu.mealpilot.core.ai.ChatPrompts
import hu.mealpilot.core.ai.EstimatePrompts
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
    strings: AppStrings,
    language: () -> AppLanguage,
    private val settingsProvider: suspend () -> AppSettings,
) : StreamingMealAi(strings, language) {

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
        language: AppLanguage,
        planDays: Int,
        chunkIndex: Int,
        isRetry: Boolean,
        onChars: (Int) -> Unit,
    ): String {
        val apiKey = keyStore.apiKey() ?: throw MissingApiKeyException(strings[R.string.error_missing_api_key])
        val settings = settingsProvider()

        val builder = MessageCreateParams.builder()
            .model(settings.model.id)
            .maxTokens(task.maxOutputTokens)
            .systemOfTextBlockParams(
                listOf(
                    TextBlockParam.builder()
                        // Kimerítő `when`, nem `if/else`: az else ág CSENDBEN a tervező
                        // promptját küldte volna minden új feladattípushoz — a becslésre
                        // egy egész étrendet kaptunk volna vissza. Így a fordító kényszerít
                        // döntésre, valahányszor az AiTask bővül.
                        // A `language` itt a HÍVÁS paramétere, és szándékosan takarja
                        // az ősosztály felületnyelv-mezőjét: egy kész terv átírásakor
                        // a terv nyelve a helyes, nem az, amit a felhasználó azóta
                        // átállított.
                        .text(
                            when (task) {
                                AiTask.CHAT -> ChatPrompts.system(language)
                                AiTask.ESTIMATE -> EstimatePrompts.system(language)
                                AiTask.PLAN, AiTask.DAY -> PlanPrompts.system(language)
                            }
                        )
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
                // Soronként megkérdezzük, kell-e még. Enélkül a „Mégsem" csak a
                // felületet állította meg: a folyam a végéig befolyt, és a hívás a
                // felhasználó SAJÁT Anthropic-számláján a teljes tervet kifizette.
                // A `use` a ciklusból kilépve bontja a kapcsolatot — ettől áll meg
                // tényleg a generálás.
                coroutineContext.ensureActive()
                val delta = events.next().contentBlockDelta().orElse(null) ?: continue
                val textDelta = delta.delta().text().orElse(null) ?: continue
                text.append(textDelta.text())
                onChars(text.length)
            }
        }
        coroutineContext.ensureActive()
        if (text.isBlank()) throw EmptyResponseException(strings[R.string.error_empty_response])
        return text.toString()
    }

    private fun AiEffort.toApiEffort(): OutputConfig.Effort = when (this) {
        AiEffort.LOW -> OutputConfig.Effort.LOW
        AiEffort.MEDIUM -> OutputConfig.Effort.MEDIUM
        AiEffort.HIGH -> OutputConfig.Effort.HIGH
    }

    internal override fun translate(error: Throwable): Throwable = when (error) {
        is CancellationException -> error
        is MealAiException -> error
        is UnauthorizedException -> MealAiException(strings[R.string.error_bad_api_key], error)
        is RateLimitException -> MealAiException(strings[R.string.error_rate_limited], error)
        is BadRequestException -> MealAiException(
            strings[R.string.error_bad_request, error.message ?: strings[R.string.error_unknown_reason]],
            error,
        )
        is AnthropicServiceException -> MealAiException(
            strings[
                R.string.error_service,
                error.errorType().map { it.toString() }.orElse(strings[R.string.error_unknown_reason]),
            ],
            error,
        )
        is java.io.IOException -> MealAiException(strings[R.string.error_no_network_ai], error)
        // A NYERS kivételüzenet nem kerül a felhasználó elé. Ide csak keretrendszeri
        // hibák jutnak — a saját, már megfogalmazott üzeneteink `MealAiException`-ként
        // fentebb kilépnek —, és azok üzenete gépi szöveg: egy elromlott válaszból
        // „Unexpected JSON token at offset 1247: Expected '}'… at path: $.days[2]"
        // lett a hibaüzenet a képernyőn. Az eredeti hiba OKKÉNT megmarad, tehát az
        // összeomlás-jelentésben és a naplóban ott van.
        else -> MealAiException(strings[R.string.error_unknown_planning], error)
    }
}
