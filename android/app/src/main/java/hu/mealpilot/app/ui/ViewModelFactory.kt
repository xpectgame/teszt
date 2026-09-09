package hu.mealpilot.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import hu.mealpilot.app.AppContainer

/** Rövid segédfüggvény, hogy minden képernyő egy sorban meg tudja adni a ViewModel-jét. */
inline fun <reified VM : ViewModel> containerFactory(
    container: AppContainer,
    crossinline create: (AppContainer) -> VM,
) = viewModelFactory {
    initializer { create(container) }
}
