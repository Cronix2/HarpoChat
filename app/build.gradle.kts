import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
}

// ---------------------------------------------------------------------------
// Racines de confiance du mesh (spec §4.4) : identité publique et adresses des
// nœuds racines. Aucun secret ici : ce sont les ancres contre lesquelles l'app
// vérifie l'annuaire et l'identité Noise de chaque nœud.
// Format : "<id_pub hex>@<endpoint>[,<endpoint>…]" ; plusieurs racines séparées par ';'.
// ---------------------------------------------------------------------------
val releaseRoots = "cb86ae6063f2c93c5289f910f2f96651ea89fd19bed465a9381f82099b0fe43e@wss://harpo.cronix.fr:443"

// Une build de debug peut viser un nœud de développement : `mesh.roots.debug=…` dans local.properties.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val debugRoots: String = localProps.getProperty("mesh.roots.debug") ?: releaseRoots

/** Bloc d'amorçage CBOR `{ v: 1, roots: [{ id_pub, endpoints }] }`, attendu par `decodeBootstrap`. */
fun bootstrapCbor(spec: String): ByteArray {
    val out = ByteArrayOutputStream()
    fun head(major: Int, len: Int) {
        val m = major shl 5
        when {
            len < 24 -> out.write(m or len)
            len < 256 -> { out.write(m or 24); out.write(len) }
            else -> { out.write(m or 25); out.write(len shr 8); out.write(len and 0xff) }
        }
    }
    fun text(s: String) = s.toByteArray(Charsets.UTF_8).let { head(3, it.size); out.write(it) }
    fun bytes(b: ByteArray) { head(2, b.size); out.write(b) }
    val roots = spec.split(';').map { it.trim() }.filter { it.isNotEmpty() }.map { entry ->
        val (id, eps) = entry.split('@', limit = 2)
        require(id.length == 64) { "id_pub de racine invalide : $id" }
        id.chunked(2).map { it.toInt(16).toByte() }.toByteArray() to eps.split(',').map { it.trim() }
    }
    require(roots.isNotEmpty()) { "aucune racine de mesh" }
    head(5, 2)
    text("v"); head(0, 1)
    text("roots"); head(4, roots.size)
    roots.forEach { (id, eps) ->
        head(5, 2)
        text("id_pub"); bytes(id)
        text("endpoints"); head(4, eps.size); eps.forEach { text(it) }
    }
    return out.toByteArray()
}

/**
 * Obscurcit le bloc (XOR avec un flux SHA-256) pour qu'aucune adresse ne se lise en clair
 * dans l'APK. Ce n'est pas un secret : une analyse du code le retrouve. Inverse : `net.Roots`.
 */
fun obscured(spec: String): String {
    val plain = bootstrapCbor(spec)
    val stream = ByteArrayOutputStream()
    var counter = 0
    while (stream.size() < plain.size) {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("calc-layout-v1".toByteArray())
        md.update(byteArrayOf((counter shr 24).toByte(), (counter shr 16).toByte(), (counter shr 8).toByte(), counter.toByte()))
        stream.write(md.digest()); counter++
    }
    val ks = stream.toByteArray()
    return Base64.getEncoder().encodeToString(ByteArray(plain.size) { (plain[it].toInt() xor ks[it].toInt()).toByte() })
}

android {
    namespace = "app.harpochat"
    compileSdk = 36

    defaultConfig {
        // Neutral id: what Settings > Apps and adb show. Never "harpochat".
        applicationId = "app.calc.scientific"
        minSdk = 26
        targetSdk = 36
        // Fournis par la CI de release (numéro d'exécution) ; valeurs de développement sinon.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Architectures fournies par le noyau harpochat-core : les autres (mips, x86 32 bits,
        // apportées par JNA ou SQLCipher) planteraient au premier appel réseau.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    // Signature de release fournie par la CI (secrets GitHub) : jamais de clé dans le dépôt.
    val releaseStoreFile = System.getenv("RELEASE_STORE_FILE")
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            buildConfigField("String", "LAYOUT_TABLE", "\"${obscured(debugRoots)}\"")
        }
        release {
            buildConfigField("String", "LAYOUT_TABLE", "\"${obscured(releaseRoots)}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // ---------- Compose ----------
    implementation(platform(libs.androidx.compose.bom.v20250801))
    implementation(libs.androidx.core.ktx.v1170)
    implementation(libs.androidx.lifecycle.runtime.ktx.v293)
    implementation(libs.androidx.activity.compose.v1101)
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.ui.test.manifest)

    // ---------- Architecture ----------
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    // ---------- Stockage chiffré : Room + SQLCipher (16 KB pages) ----------
    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    // ---------- QR : scan via Google Code Scanner (aucune permission CAMERA) ----------
    implementation(libs.play.services.code.scanner)
    // Génération de QR : ZXing core seul (zxing-android-embedded déclarait CAMERA)
    implementation(libs.core)

    // ---------- Réseau : noyau harpochat-core (bindings uniffi via JNA) + WebSocket OkHttp ----------
    implementation("net.java.dev.jna:jna:5.17.0@aar")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ---------- Ed25519 (format d'invitation transitoire, remplacé par harpochat-core) ----------
    implementation("net.i2p.crypto:eddsa:0.3.0")

    // ---------- Tests ----------
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom.v20250801))
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit.v130)
    androidTestImplementation(libs.androidx.espresso.core.v370)
}
