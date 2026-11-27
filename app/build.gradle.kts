plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
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
        }
        release {
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
