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
    implementation(platform("androidx.compose:compose-bom:2024.09.01"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended") // version via BOM

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // ---------- Tests ----------
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // ---------- Architecture / Coroutines ----------
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ---------- Sécurité ----------
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")

    // ---------- Protocole Signal ----------
    implementation("org.whispersystems:signal-protocol-java:2.8.1")

    // ---------- Room (KSP, pas kapt) ----------
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ---------- SQLCipher (pour SupportFactory dans AppDatabase) ----------
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // ---------- ML Kit Barcode ----------
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // ---------- CameraX ----------
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // ---------- ZXing (génération QR) ----------
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
}
