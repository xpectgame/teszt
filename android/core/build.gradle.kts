plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    // A tervezők felfüggesztő függvények — a teszteléshez kell egy futtató.
    testImplementation(libs.kotlinx.coroutines.test)
}
