// Minden plugin itt, a gyökérben van deklarálva (alkalmazás nélkül), a modulok pedig
// verzió nélkül hivatkoznak rájuk. Ez az Android projektek bevett elrendezése, és két
// dolgot old meg: a Kotlin plugin nem töltődik be modulonként külön, és a Kotlin Android
// plugin ugyanazon a classpath-en látja az Android Gradle Plugint, amire szüksége van.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
