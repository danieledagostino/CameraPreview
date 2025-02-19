plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "org.dd.camerapreview"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.dd.camerapreview"
        minSdk = 28
        targetSdk = 35
        versionCode = 13
        versionName = "2.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "armeabi") // Architetture supportate
        }
    }

    buildTypes {
        release {
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    ndkVersion = "27.0.12077973"
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.ffmpeg.kit) // Usa la libreria FFmpegKit
    implementation("com.google.android.gms:play-services-ads:23.6.0")
}