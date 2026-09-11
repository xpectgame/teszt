package hu.mealpilot.app.data.ai

import hu.mealpilot.app.data.remote.BackendClient
import kotlinx.coroutines.CancellationException

/**
 * Modellhívás a saját backenden keresztül — ez a bolti build valódi kiszolgálója.
 *
 * A különbség a [AnthropicMealAi]-hoz képest nem a felhasználó felé látszik, hanem
 * abban, hogy mi marad a telefonon: itt nincs API kulcs, és a jogosultságról sem a
 * kliens dönt. A darabolás, az ellenőrzés és a javító kör változatlanul helyben fut,
 * mert azokhoz nem kell szerver.
 */
class BackendMealAi(private val backend: BackendClient) : StreamingMealAi() {

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
        onChars = onChars,
    )

    override fun translate(error: Throwable): Throwable = when (error) {
        is CancellationException -> error
        is MealAiException -> error
        is java.io.IOException -> MealAiException(
            "Nem sikerült elérni a szolgáltatást. Ellenőrizd az internetkapcsolatot.", error
        )
        else -> MealAiException(error.message ?: "Ismeretlen hiba a terv készítése közben.", error)
    }
}
