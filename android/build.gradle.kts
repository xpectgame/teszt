// A Kotlin pluginokat itt, a közös szülőben deklaráljuk (alkalmazás nélkül), a modulok
// pedig verzió nélkül hivatkoznak rájuk. Enélkül a Kotlin plugin modulonként külön
// töltődne be, amire a fordító jogosan figyelmeztet.
// Az Android Gradle Plugin szándékosan csak az :app modulban szerepel, így a tisztán
// Kotlin :core modul Android SDK nélkül is fordítható és tesztelhető.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
