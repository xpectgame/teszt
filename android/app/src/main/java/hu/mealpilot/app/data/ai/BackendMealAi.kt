package hu.mealpilot.app.data.ai

import hu.mealpilot.app.R
import hu.mealpilot.app.data.remote.BackendClient
import hu.mealpilot.app.i18n.AppStrings
import hu.mealpilot.core.i18n.AppLanguage
import kotlinx.coroutines.CancellationException

/**
 * Modellhívás a saját backenden keresztül — ez a bolti build valódi kiszolgálója.
 *
 * A különbség a [AnthropicMealAi]-hoz képest nem a felhasználó felé látszik, hanem
 * abban, hogy mi marad a telefonon: itt nincs API kulcs, és a jogosultságról sem a
 * kliens dönt. A darabolás, az ellenőrzés és a javító kör változatlanul helyben fut,
 * mert azokhoz nem kell szerver.
 */
class BackendMealAi(
    private val backend: BackendClient,
    strings: AppStrings,
    language: AppLanguage,
) : StreamingMealAi(strings, language) {

    override val isConfigured: Boolean get() = backend.isConfigured

    override suspend fun call(
        task: AiTask,
        userText: String,
        planDays: Int,
        chunkIndex: Int,
        isRetry: Boolean,
        onChars: (Int) -> Unit,
    ): String = backend.generate(
        task = task.name,
        prompt = userText,
        days = planDays,
        chunkIndex = chunkIndex,
        isRetry = isRetry,
        language = language.tag,
        onChars = onChars,
    )

    override fun translate(error: Throwable): Throwable = when (error) {
        is CancellationException -> error
        is MealAiException -> error
        is java.io.IOException -> MealAiException(strings[R.string.error_no_network_service], error)
        else -> MealAiException(error.message ?: strings[R.string.error_unknown_planning], error)
    }
}
