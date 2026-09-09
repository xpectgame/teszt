// A pluginokat modulonként deklaráljuk (lásd core/build.gradle.kts, app/build.gradle.kts),
// így a tisztán Kotlin :core modul az Android Gradle Plugin nélkül is fordítható és tesztelhető.
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
