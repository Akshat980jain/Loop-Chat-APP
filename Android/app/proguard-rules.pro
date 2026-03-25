# ============================================
# ProGuard / R8 Rules for Loop Chat App
# ============================================

# ---------- Ktor & Kotlinx Serialization ----------
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

-keep class kotlinx.serialization.** { *; }
-keep interface kotlinx.serialization.** { *; }
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
    @kotlinx.serialization.SerialName *;
}
-keepnames class kotlinx.serialization.internal.**
-keepclassmembers class kotlinx.serialization.internal.** {
    public <init>(...);
}

# ---------- Data Models ----------
# Keep our data models so serialization doesn't break
-keep class com.loopchat.app.data.models.** { *; }
-keepclassmembers class com.loopchat.app.data.models.** { *; }
-keep class com.loopchat.app.ui.screens.StarredMessageItem { *; }
-keep class com.loopchat.app.ui.screens.StarredEntry { *; }
-keep class com.loopchat.app.data.MatchedContact { *; }

# ---------- Supabase GoTrue / PostgREST ----------
-keep class io.github.jan.supabase.** { *; }
-keep interface io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# ---------- Jetpack Compose ----------
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# ---------- Coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ---------- Firebase Crashlytics & Analytics ----------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# ---------- Coil (Image Loading) ----------
-keep class coil.** { *; }
-dontwarn coil.**
-dontwarn okio.**

# ---------- Room Database ----------
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}

# ---------- Google ML Kit & ZXing ----------
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ---------- Daily.co (Video Calls) ----------
-keep class co.daily.** { *; }
-dontwarn co.daily.**
-keepclassmembers class co.daily.** { *; }
