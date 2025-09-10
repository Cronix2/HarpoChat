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
    val composebomVersion = "2025.08.01"
    implementation(platform("androidx.compose:compose-bom:$composebomVersion"))

    val coreVersion = "1.17.0"
    val lifecycleVersion = "2.9.3"
    val activityVersion = "1.10.1"
    implementation("androidx.core:core-ktx:$coreVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.activity:activity-compose:$activityVersion")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended") // version via BOM

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:$composebomVersion"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // ---------- Tests ----------
    val junitVersion = "4.13.2"
    val androidJunitVersion = "1.3.0"
    val espressoVersion = "3.7.0"
    testImplementation("junit:junit:$junitVersion")
    androidTestImplementation("androidx.test.ext:junit:$androidJunitVersion")
    androidTestImplementation("androidx.test.espresso:espresso-core:$espressoVersion")

    // ---------- Architecture / Coroutines ----------
    val kotlinVersion = "1.10.2"
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$kotlinVersion")

    // ---------- Sécurité ----------
    val securityVersion = "1.1.0"
    implementation("androidx.security:security-crypto-ktx:$securityVersion")

    // ---------- Protocole Signal ----------
    val signalVersion = "2.8.1"
    implementation("org.whispersystems:signal-protocol-java:$signalVersion")

    // ---------- Room (KSP, pas kapt) ----------
    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ---------- SQLCipher (pour SupportFactory dans AppDatabase) ----------
    val sqlcipherVersion = "4.5.4"
    val sqliteVersion = "2.5.2"
    implementation("net.zetetic:android-database-sqlcipher:$sqlcipherVersion")
    implementation("androidx.sqlite:sqlite-ktx:$sqliteVersion")

    // ---------- ML Kit Barcode ----------
    val mlkitVersion = "17.3.0"
    implementation("com.google.mlkit:barcode-scanning:$mlkitVersion")

    // ---------- CameraX ----------
    val camerax = "1.4.2"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // ---------- ZXing (génération QR) ----------
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
}
