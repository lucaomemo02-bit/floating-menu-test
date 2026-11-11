plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.floatingmenu"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.floatingmenu"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // NotificationCompat e ServiceCompat (notificação do serviço em primeiro plano)
    implementation("androidx.core:core-ktx:1.13.1")
}
