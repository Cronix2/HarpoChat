plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt") // nécessaire pour Room        // <= pour l’annotation processor Room
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ViewModel pour Jetpack Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    // Sécurité : stockage chiffré via Keystore
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Protocole Signal (Java)
    implementation("org.whispersystems:signal-protocol-java:2.8.1")

    implementation("androidx.compose.material:material-icons-extended:<compose-version>")

    // ========= Room =========
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // ========= SQLCipher (SupportFactory) =========
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")

    // (optionnel) sqlite-ktx utilitaires
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Compose (si pas déjà)
    implementation("androidx.compose.runtime:runtime:1.6.8")
    implementation("androidx.activity:activity-compose:1.9.0")

}