import java.util.Properties

/**
 * A jogi oldalak címe, ha sem a backend, sem a MEALPILOT_SITE_URL nincs megadva.
 *
 * Rendes esetben nem ez fut: a backend maga is kiszolgálja a jogi oldalakat, tehát a
 * MEALPILOT_BACKEND_URL egyben a weboldal címe is. Lásd docs/DOMAIN.md.
 */
val FALLBACK_SITE_URL = "https://xpectgame.github.io/teszt/mealpilot"

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "hu.mealpilot.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "hu.mealpilot.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        // A CI a futás sorszámát is beleírja, hogy a telefonon a Névjegyben látszódjon,
        // melyik buildet használod éppen.
        versionName = System.getenv("MEALPILOT_BUILD")
            ?.takeIf { it.isNotBlank() }
            ?.let { "0.1.0 ($it)" }
            ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("hu", "en")

        // A saját backend címe. Ha üres, az app a felhasználó saját API kulcsát kéri
        // (fejlesztői út), vagy az offline tervezőt használja — tehát build nélkül is fut.
        // Bolti kiadáshoz KÖTELEZŐ beállítani, mert egy fizető felhasználó nem szerez
        // Anthropic-kulcsot, és a kvótát sem dönthetné el a telefon.
        //   MEALPILOT_BACKEND_URL=https://... ./gradlew :app:assembleRelease
        val backendUrl = System.getenv("MEALPILOT_BACKEND_URL")?.trim()?.trimEnd('/').orEmpty()
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")

        // A jogi oldalak és a támogatás címe.
        //
        // Alapból ugyanaz, mint a backendé: a Worker a jogi oldalakat is kiszolgálja,
        // tehát nem kell hozzá se külön tárhely, se domain, és a szöveg ugyanazzal a
        // deployjal frissül, mint a kód. Saját domainnel felülírható:
        //   MEALPILOT_SITE_URL=https://mealpilot.hu ./gradlew :app:bundleRelease
        val siteUrl = System.getenv("MEALPILOT_SITE_URL")?.trim()?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: backendUrl.ifBlank { FALLBACK_SITE_URL }
        buildConfigField("String", "SITE_URL", "\"$siteUrl\"")
    }

    // A kiadási aláíráshoz szükséges adatok. SOHA nem kerülnek a repóba: vagy a
    // gitignore-olt keystore.properties fájlból jönnek, vagy környezeti változókból (CI).
    val releaseKeystore = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    fun releaseSecret(key: String, env: String): String? =
        (releaseKeystore.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

    val releaseStorePath = releaseSecret("storeFile", "MEALPILOT_KEYSTORE")
    val releaseStorePassword = releaseSecret("storePassword", "MEALPILOT_KEYSTORE_PASSWORD")
    val releaseKeyAlias = releaseSecret("keyAlias", "MEALPILOT_KEY_ALIAS")
    val releaseKeyPassword = releaseSecret("keyPassword", "MEALPILOT_KEY_PASSWORD")
    val canSignRelease = releaseStorePath != null && releaseStorePassword != null &&
        releaseKeyAlias != null && releaseKeyPassword != null && file(releaseStorePath).exists()

    signingConfigs {
        // Rögzített debug kulcs a repóban. Enélkül minden CI-futás új kulcsot generálna,
        // és a telefonon lévő korábbi verzióra nem lehetne ráfrissíteni — az étkezés-,
        // súly- és mozgásnapló minden frissítéskor elveszne.
        // Ez KIZÁRÓLAG debug build aláírására való. Éles kiadáshoz külön, titkos kulcs kell,
        // ami soha nem kerülhet verziókezelésbe.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // Csak akkor jön létre, ha tényleg van mivel aláírni. Enélkül az
        // `assembleRelease` aláíratlan APK-t ad — fordul, de a Play nem fogadja el.
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    // A fejlesztő saját kulcsa: aki ezt küldi, a backenden kvóta nélkül dolgozik, és az
    // appban is teljes csomagot lát. A saját telefonodra szánt buildbe kell, a Play-re
    // feltöltöttbe SOHA — ezért a release build alapból nem kapja meg.
    val ownerKey = System.getenv("MEALPILOT_OWNER_KEY")?.trim().orEmpty()
    val ownerKeyInRelease = System.getenv("MEALPILOT_OWNER_KEY_IN_RELEASE") == "1"
    if (ownerKey.isNotBlank() && ownerKeyInRelease) {
        logger.warn(
            "FIGYELEM: a tulajdonosi kulcs bekerül a RELEASE buildbe. Ezt a csomagot ne " +
                "töltsd fel a Play Console-ba — a kulcs visszafejthető lenne belőle."
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
            buildConfigField(
                "String",
                "OWNER_KEY",
                "\"${if (ownerKeyInRelease) ownerKey else ""}\"",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "OWNER_KEY", "\"$ownerKey\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Hivatalos Anthropic Java SDK — a Kotlin ezt használja.
    implementation(libs.anthropic.java)

    // Google Play Billing az előfizetéshez.
    implementation(libs.billing.ktx)

    // A saját backend hívásához. Az Anthropic SDK is ezt használja, de nem hagyatkozunk
    // a tranzitív függőségre: a verziót mi rögzítjük.
    implementation(libs.okhttp)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
