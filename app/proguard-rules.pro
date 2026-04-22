# ============================================================================
# HarpoChat ProGuard / R8 rules
# SPDX-License-Identifier: MIT
# ============================================================================

# Keep line numbers for readable crash reports, but rewrite source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Kotlin / Compose / AndroidX
# ---------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# Compose relies on reflection into @Stable/@Immutable etc.
-dontwarn kotlin.reflect.jvm.internal.**

# ---------------------------------------------------------------------------
# Room (generated DAO/DB classes)
# ---------------------------------------------------------------------------
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# SQLCipher (net.zetetic) — reflection to load JNI symbols
# ---------------------------------------------------------------------------
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ---------------------------------------------------------------------------
# Signal protocol (libsignal-client)
# ---------------------------------------------------------------------------
-keep class org.signal.** { *; }
-keep class org.whispersystems.** { *; }
-dontwarn org.signal.**
-dontwarn org.whispersystems.**

# ---------------------------------------------------------------------------
# EdDSA (net.i2p.crypto:eddsa) — used by our invite signing
# ---------------------------------------------------------------------------
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn net.i2p.crypto.eddsa.**

# ---------------------------------------------------------------------------
# OkHttp / Okio (used by RelayWsTransport)
# ---------------------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# ML Kit / CameraX
# ---------------------------------------------------------------------------
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ---------------------------------------------------------------------------
# ZXing
# ---------------------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class com.journeyapps.barcodescanner.** { *; }

# ---------------------------------------------------------------------------
# Application entry points (Activities, Services, Receivers)
# ---------------------------------------------------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ---------------------------------------------------------------------------
# Reflection safety nets for data classes accessed via JSON
# ---------------------------------------------------------------------------
-keepclassmembers class app.harpochat.link.Invite { *; }
-keepclassmembers class app.harpochat.transport.** { *; }
