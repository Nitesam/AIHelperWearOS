# LaTeX renderer
-keep class io.nano.tex.** {*;}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.base.aihelperwearos.**$$serializer { *; }
-keepclassmembers class com.base.aihelperwearos.** {
    *** Companion;
}
-keepclasseswithmembers class com.base.aihelperwearos.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.atomicfu.**
-dontwarn io.netty.**
-dontwarn com.typesafe.**
-dontwarn org.slf4j.**

# Coil
-keep class coil.** { *; }
-keep class coil.compose.** { *; }

# Keep data classes for JSON serialization
-keep class com.base.aihelperwearos.data.models.** { *; }
-keep class com.base.aihelperwearos.data.repository.** { *; }
-keep class com.base.aihelperwearos.data.rag.models.** { *; }

# Remove debug logs in release
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
