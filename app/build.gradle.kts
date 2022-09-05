plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    compileSdk = 33

    defaultConfig {
        applicationId = "org.lineageos.aperture"
        minSdk = 31
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        named("release") {
            // Enables code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            // Includes the default ProGuard rules files.
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.appcompat:appcompat:1.5.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("com.google.android.material:material:1.8.0-alpha01")

    // CameraX core library using the camera2 implementation
    val cameraxVersion = "1.2.0-beta02"
    // The following line is optional, as the core library is included indirectly by camera-camera2
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    // If you want to additionally use the CameraX Lifecycle library
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    // If you want to additionally use the CameraX VideoCapture library
    implementation("androidx.camera:camera-video:${cameraxVersion}")
    // If you want to additionally use the CameraX View class
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    // If you want to additionally use the CameraX Extensions library
    implementation("androidx.camera:camera-extensions:${cameraxVersion}")

    // ZXing
    implementation("com.google.zxing:core:3.5.0")

    // Coil
    implementation("io.coil-kt:coil:2.2.0")
    implementation("io.coil-kt:coil-video:2.2.0")
}
