plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // sans version explicite
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

    // ✅ Compat AGP 8+ (et on garde aussi le bloc "packagingOptions" AGP 7.x juste en dessous)
    packaging {
        resources {
            // On force la prise de la 1re occurrence pour TOUS les modèles ML Kit barcode
            pickFirsts += listOf(
                "assets/mlkit_barcode_models/barcode_ssd_mobilenet_v1_dmp25_quant.tflite",
                "assets/mlkit_barcode_models/oned_feature_extractor_mobile.tflite",
                "assets/mlkit_barcode_models/oned_auto_regressor_mobile.tflite"
            )
            // au cas où la lib les expose sans le préfixe assets/ (selon la phase de merge) :
            pickFirsts += listOf(
                "mlkit_barcode_models/barcode_ssd_mobilenet_v1_dmp25_quant.tflite",
                "mlkit_barcode_models/oned_feature_extractor_mobile.tflite",
                "mlkit_barcode_models/oned_auto_regressor_mobile.tflite"
            )
            // et un filet de sécurité générique :
            pickFirsts += listOf("**/mlkit_barcode_models/*.tflite")
        }
    }

    // ✅ Compat AGP 7.x (si jamais ton projet est sur une version antérieure)
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
    implementation(platform("androidx.compose:compose-bom:2024.09.01"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.whispersystems:signal-protocol-java:2.8.1")

    // ✅ Une seule dépendance ML Kit Barcode
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    // ❌ Vérifie qu'il n'y a PAS aussi :
    // implementation("com.google.android.gms:play-services-mlkit-barcode-scanning")
}
