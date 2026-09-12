# --- Anthropic Java SDK + Jackson ---
# A JSON (de)szerializáció reflexiót használ, ezért a modellosztályok nem kaphatnak
# obfuszkált nevet, és a Jackson annotációknak is meg kell maradniuk.
-keep class com.anthropic.** { *; }
-keepclassmembers class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn com.anthropic.**

# Az Anthropic SDK behúzza a victools jsonschema-generatort (ez rajzolja meg a
# strukturált kimenet / eszközhasználat sémáit). Az a könyvtár olyan reflexiós
# típusokra hivatkozik, amik CSAK a rendes JVM-en léteznek, Androidon nincsenek meg:
# java.lang.reflect.AnnotatedType és társai. Az R8 a hiányzó osztályokat hibának
# veszi, és megáll — emiatt a kiadási build egyáltalán nem fordult le, miközben a
# debug (ahol nincs R8) végig működött.
#
# Az appot ez nem érinti: nem használunk strukturált kimenetet, csak sima szöveges
# üzeneteket, tehát ezek a kódutak sosem futnak.
-dontwarn com.github.victools.**
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedParameterizedType

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization: a generált serializer-eket meg kell tartani.
#
# A `$$serializer` osztályokat a fordító generálja, és NÉV SZERINT keresi meg őket a
# futásidő. Ha az R8 kidobja vagy átnevezi őket, az étrend JSON-je csak a KIADÁSI
# buildben nem áll össze — debugban minden működik, tehát a hiba pont a boltba
# feltöltött csomagban jelenne meg először.
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep,includedescriptorclasses class hu.mealpilot.**$$serializer { *; }
-keepclassmembers class hu.mealpilot.** {
    *** Companion;
}
-keepclasseswithmembers class hu.mealpilot.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Google Play Billing ---
# A könyvtár hoz saját szabályokat; ez csak biztonsági háló a modellosztályokra.
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
