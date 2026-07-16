plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.marciano.smartreader"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.marciano.smartreader"
        minSdk = 26
        targetSdk = 34
        // Verzování: dvě desetinná místa, prosté postupné číslování
        // (2.11 -> 2.12 -> 2.13...). Celé číslo před tečkou (2.x -> 3.0) se mění
        // jen u zásadní změny, a to vždy po výslovné dohodě předem.
        versionCode = 37
        versionName = "2.18"
    }

    signingConfigs {
        getByName("debug") {
            // Pevný, do repozitáře uložený debug klíč - používá se jen pro lokální
            // vývoj v Android Studiu (debug build type).
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            // Vlastní unikátní klíč (jiné heslo, jiný alias než univerzální debug
            // klíč) - appka distribuovaná uživatelům se podepisuje tímhle.
            storeFile = file("../release.keystore")
            storePassword = "nMNFP8dl8frJJwwtzoZBzjae"
            keyAlias = "chytraCteckaTextuKey"
            keyPassword = "nMNFP8dl8frJJwwtzoZBzjae"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    // Jsoup - parsování HTML pro vytažení čitelného textu z odkazů (např. novinové články)
    implementation("org.jsoup:jsoup:1.17.2")
}
