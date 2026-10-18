# Keep the ML Kit text recogniser entry points: it is loaded reflectively by the SDK.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text** { *; }
-dontwarn com.google.mlkit.**

# OkHttp / Conscrypt
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
