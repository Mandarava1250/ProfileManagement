# ProGuard 规则：Release 构建时生效

# === Kotlinx Serialization ===
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.personalassistant.**$$serializer { *; }
-keepclassmembers class com.example.personalassistant.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.personalassistant.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# === Room ===
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# === Hilt / Dagger ===
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# === Compose ===
-keep class androidx.compose.runtime.** { *; }

# === Coroutines ===
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# === MPAndroidChart ===
-keep class com.github.mikephil.charting.** { *; }

# === 实体类（被 Room 和 Serialization 反射使用）===
-keep class com.example.personalassistant.feature.**.data.local.**Entity { *; }
