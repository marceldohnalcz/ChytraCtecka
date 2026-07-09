plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.marceldohnalcz.smartreader"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.marceldohnalcz.smartreader"
        minSdk = 26
        targetSdk = 34
        // Verzování: číslo za tečkou (3.0 -> 3.1 -> 3.2...) se zvyšuje u běžných
        // úprav a oprav. Celé číslo před tečkou (3.0 -> 4.0) jen u zásadní změny.
        // 3.0 = přejmenování balíčku (com.example.* -> io.github.*) + vlastní
        // release podpisový klíč místo sdíleného debug klíče.
        versionCode = 19
        versionName = "3.0"
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
