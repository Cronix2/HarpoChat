plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Room avec KSP (aligne la version si tu changes ta version de Kotlin)
    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
}

android {
    namespace = "com.example.harpochat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.harpochat"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // ✅ AGP 8+
    packaging {
        resources {
            // Conflits ML Kit (une seule occurrence des modèles)
            pickFirsts += listOf(
                "assets/mlkit_barcode_models/barcode_ssd_mobilenet_v1_dmp25_quant.tflite",
                "assets/mlkit_barcode_models/oned_feature_extractor_mobile.tflite",
                "assets/mlkit_barcode_models/oned_auto_regressor_mobile.tflite",
                "mlkit_barcode_models/barcode_ssd_mobilenet_v1_dmp25_quant.tflite",
                "mlkit_barcode_models/oned_feature_extractor_mobile.tflite",
                "mlkit_barcode_models/oned_auto_regressor_mobile.tflite",
                "**/mlkit_barcode_models/*.tflite"
            )
        }
    }

    // ✅ Compat AGP 7.x si nécessaire (peut être supprimé si tu es sûr d'être en 8+)
    @Suppress("DEPRECATION")
    packagingOptions {
        resources {
            pickFirsts += setOf(
                "assets/mlkit_barcode_models/barcode_ssd_mobilenet_v1_dmp25_quant.tflite",
                "assets/mlkit_barcode_models/oned_feature_extractor_mobile.tflite",
                "assets/mlkit_barcode_models/oned_auto_regressor_mobile.tflite",
                "mlkit_barcode_models/barcode_ssd_mobilenet_v1_dmp25_quant.tflite",
                "mlkit_barcode_models/oned_feature_extractor_mobile.tflite",
                "mlkit_barcode_models/oned_auto_regressor_mobile.tflite",
                "**/mlkit_barcode_models/*.tflite"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
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
    implementation(libs.material.icons.extended) // version via BOM

    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.ui.test.manifest)
    androidTestImplementation(libs.androidx.compose.bom.v20250801)
    androidTestImplementation(libs.ui.test.junit4)

    // ---------- Tests ----------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v130)
    androidTestImplementation(libs.androidx.espresso.core.v370)

    // ---------- Architecture / Coroutines ----------
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    // ---------- Sécurité ----------
    implementation(libs.androidx.security.crypto.ktx)

    // ---------- Protocole Signal ----------
    implementation(libs.signal.protocol.java)

    // ---------- Room (KSP, pas kapt) ----------
    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ---------- SQLCipher (pour SupportFactory dans AppDatabase) ----------

    implementation(libs.android.database.sqlcipher)
    implementation(libs.androidx.sqlite.ktx)

    // ---------- ML Kit Barcode ----------
    implementation(libs.barcode.scanning)

    // ---------- CameraX ----------
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ---------- ZXing (génération QR) ----------
    implementation(libs.zxing.android.embedded)
    implementation(libs.core)
}
