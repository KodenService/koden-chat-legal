import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signature de publication : fournie par GitHub Actions (secrets), jamais stockée dans le code.
val keystorePath: String? = System.getenv("KODEN_KEYSTORE_PATH")
val hasReleaseKey = !keystorePath.isNullOrBlank() && file(keystorePath!!).exists()

android {
    namespace = "ci.kodens.chat"
    compileSdk = 36

    defaultConfig {
        applicationId = "ci.kodens.chat"
        minSdk = 26
        targetSdk = 36   // exigé par Google Play depuis le 31 août 2026
        // Chaque compilation GitHub incrémente le numéro de version (obligatoire pour le Play Store).
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.3.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("KODEN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KODEN_KEY_ALIAS")
                keyPassword = System.getenv("KODEN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Transport hors ligne : Bluetooth + Wi-Fi Direct
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.8")  // exigé par registerForActivityResult

    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Chiffrement de bout en bout (libsodium via Lazysodium)
    implementation("com.goterl:lazysodium-android:5.1.0@aar")
    implementation("net.java.dev.jna:jna:5.14.0@aar")
}
