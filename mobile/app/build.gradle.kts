plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.facefit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.facefit"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    // ── 16 KB page-size compatibility ────────────────────────────────────────
    // Newer devices (Samsung Galaxy S25, etc.) ship a 16 KB page-size kernel.
    // Prebuilt .so files from TFLite/LiteRT, MediaPipe, Filament, and OpenCV
    // must be zip-aligned at 16384-byte boundaries inside the APK.
    // This packaging block re-aligns every native library at pack time without
    // requiring a recompile of the prebuilt SOs — it eliminates the
    // "This app isn't 16 KB-compatible" warning on affected devices.
    packaging {
        jniLibs {
            // Re-align prebuilt .so files to 16 KB page boundaries.
            // AGP 8.3+ honours android.bundle.enableUncompressedNativeLibs by
            // default; the useLegacyPackaging = false here ensures they are
            // stored uncompressed so the OS can mmap them directly.
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Keep Filament modules on the same release for native binary compatibility.
    implementation("com.google.android.filament:filament-android:1.65.2")
    implementation("com.google.android.filament:gltfio-android:1.65.2")
    implementation("com.google.android.filament:filament-utils-android:1.65.2")

    // CameraX
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // MediaPipe
    implementation(libs.mediapipe.tasks.vision)
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    // Try-on only: GPU option for the light face mesh (VtoLightFaceMesh); used when measured faster.
    implementation("com.google.ai.edge.litert:litert-gpu:1.4.2")
    implementation("com.google.ai.edge.litert:litert-gpu-api:1.4.2")
    implementation("org.opencv:opencv:4.12.0")

    // Google account chooser and secure ID-token exchange with Firebase Auth.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Load published retailer/frame images with caching and Compose support.
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}