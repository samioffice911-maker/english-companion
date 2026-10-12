plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Version number goes up automatically with each GitHub build
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.mahfuz.englishcompanion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mahfuz.englishcompanion"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        // A fixed key, so every new APK installs over the old one (settings are kept)
        create("companion") {
            storeFile = rootProject.file("keystore/companion.jks")
            storePassword = "companion123"
            keyAlias = "companion"
            keyPassword = "companion123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("companion")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("companion")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // No extra libraries: the app uses only built-in Android features.
}
