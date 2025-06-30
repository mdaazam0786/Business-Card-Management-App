plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.swiftcard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.swiftcard"
        minSdk = 24
        targetSdk = 35
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
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

    implementation(platform("com.google.firebase:firebase-bom:33.15.0")) // Keep this BOM for consistent versions
    implementation("com.google.firebase:firebase-analytics") // Already there
    implementation("com.google.firebase:firebase-database-ktx") // Firebase Realtime Database
    implementation("com.google.firebase:firebase-storage-ktx")

    implementation("com.google.dagger:hilt-android:2.56.2")
    kapt("com.google.dagger:hilt-android-compiler:2.56.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.0.0")




    val nav_version = "2.9.0"
    implementation("androidx.navigation:navigation-compose:$nav_version")


    implementation("io.coil-kt:coil-compose:2.2.2")


    // CameraX dependencies
<<<<<<< HEAD
    implementation(libs.androidx.camera.core)// Use the latest stable version
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view) // For PreviewView
    implementation(libs.androidx.camera.extensions)

    implementation("com.google.mlkit:text-recognition:16.0.1")
=======
    implementation("androidx.camera:camera-core:1.3.3") // Use the latest stable version
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3") // For PreviewView
    implementation("androidx.camera:camera-extensions:1.3.3")

    implementation("com.google.mlkit:text-recognition:16.0.0")
>>>>>>> d403a9bbed3c91b0f5fb5115fa24772c0ca68cc0
}