plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.drivingcheckin.car"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drivingcheckin.car"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // CarUxRestrictions (주행 제한 상태 확인)
    useLibrary("android.car")

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
    // Pleos Connect SDK
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
