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
    language: () -> AppLanguage,
) : StreamingMealAi(strings, language) {

    override val isConfigured: Boolean get() = backend.isConfigured

    override suspend fun call(
        task: AiTask,
        userText: String,
        language: AppLanguage,
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
        // A HÍVÁS nyelve megy ki, nem a felületé: a szerver ebből választ
        // rendszerpromptot, és egy kész terv átírásának a terv nyelvén kell felelnie.
        language = language.tag,
        onChars = onChars,
    )

    internal override fun translate(error: Throwable): Throwable = when (error) {
        is CancellationException -> error
        is MealAiException -> error
        is java.io.IOException -> MealAiException(strings[R.string.error_no_network_service], error)
        // A NYERS kivételüzenet nem kerül a felhasználó elé. Ide csak keretrendszeri
        // hibák jutnak — a saját, már megfogalmazott üzeneteink `MealAiException`-ként
        // fentebb kilépnek —, és azok üzenete gépi szöveg: egy elromlott válaszból
        // „Unexpected JSON token at offset 1247: Expected '}'… at path: $.days[2]"
        // lett a hibaüzenet a képernyőn. Az eredeti hiba OKKÉNT megmarad, tehát az
        // összeomlás-jelentésben és a naplóban ott van.
        else -> MealAiException(strings[R.string.error_unknown_planning], error)
    }
}
