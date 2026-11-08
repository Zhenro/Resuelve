# ---------------------------------------------------------------
# Reglas R8/ProGuard optimizadas para gama baja
# ---------------------------------------------------------------

# Optimización agresiva: menos clases/métodos => menos RAM y arranque más rápido.
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# Stack traces legibles con el mapping.txt generado.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Elimina las llamadas de log en release (cero costo en runtime).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ---- Kotlinx Serialization -------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.resuelve.preciosve.**$$serializer { *; }
-keepclassmembers class com.resuelve.preciosve.** {
    *** Companion;
}
-keepclasseswithmembers class com.resuelve.preciosve.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- OkHttp / Okio ---------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okio.**

# ---- Jsoup -----------------------------------------------------
-keepattributes Signature
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**

# ---- Coroutines ------------------------------------------------
-dontwarn kotlinx.coroutines.debug.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---- Room y Coil incluyen sus propias reglas de consumidor -----
