# --- Anthropic Java SDK + Jackson ---
# A JSON (de)szerializáció reflexiót használ, ezért a modellosztályok nem kaphatnak
# obfuszkált nevet, és a Jackson annotációknak is meg kell maradniuk.
-keep class com.anthropic.** { *; }
-keepclassmembers class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn com.anthropic.**

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
