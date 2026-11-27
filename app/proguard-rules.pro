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
# SQLCipher (net.zetetic:sqlcipher-android) — JNI
# ---------------------------------------------------------------------------
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# ---------------------------------------------------------------------------
# EdDSA (net.i2p.crypto:eddsa) — used by our invite signing
# ---------------------------------------------------------------------------
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn net.i2p.crypto.eddsa.**

# ---------------------------------------------------------------------------
# ZXing
# ---------------------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

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

# ---------------------------------------------------------------------------
# Discrétion : regroupe les classes non conservées à la racine (les noms de
# paquets internes n'apparaissent plus dans l'APK de release) et supprime les logs.
# ---------------------------------------------------------------------------
-repackageclasses ''
-renamesourcefileattribute SourceFile
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ---------------------------------------------------------------------------
# Noyau réseau harpochat-core : bindings uniffi appelés par JNA (réflexion).
# On garde les membres (noms de méthodes natives, champs des Structure) mais
# pas les noms de classes, que R8 peut renommer : le paquet n'apparaît pas en clair.
# ---------------------------------------------------------------------------
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class app.harpochat.core.** { *; }
-dontwarn java.awt.**

# OkHttp (WebSocket)
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
