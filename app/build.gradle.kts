plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.smartreader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smartreader"
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "14.0"
    }

    signingConfigs {
        getByName("debug") {
            // Pevný, do repozitáře uložený debug klíč - zajišťuje, že se každé nové
            // sestavené APK dá nainstalovat jako aktualizace té předchozí verze.
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Jsoup - parsování HTML pro vytažení čitelného textu z odkazů (např. novinové články)
    implementation("org.jsoup:jsoup:1.17.2")
}
