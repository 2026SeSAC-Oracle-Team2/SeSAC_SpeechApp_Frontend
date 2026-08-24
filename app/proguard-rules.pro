# ProGuard rules for SeSAC SpeechApp
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep DTOs for Gson serialization
-keep class com.sesac.speech.data.remote.dto.** { *; }
-keep class com.sesac.speech.data.model.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
