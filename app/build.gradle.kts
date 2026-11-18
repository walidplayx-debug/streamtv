plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    lint {
        // This optional Compose check crashes lint on some files (a lint bug); it only suggests
        // primitive state types, so it is switched off rather than letting it stop the build.
        disable += "AutoboxingStateCreation"
        // Every finding is listed in the build log too, not only in the report file.
        textReport = true
        textOutput = file("stdout")
    }
    namespace = "com.streamtv"
    // 35 for media3 1.5 (compiling against it only; the app still targets 34).
    compileSdk = 35
    defaultConfig {
        val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        applicationId = "com.streamtv"
        minSdk = 23
        targetSdk = 34
        versionCode = ciVersionCode
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // TV devices are ARM: the FFmpeg decoders for x86 (emulators) would only add size.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }
    // Release builds are signed only by CI, with the private keystore kept in the repository
    // secrets. No key lives in the repository: a build without it stays unsigned.
    val ciStore = System.getenv("STV_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (ciStore != null) {
            create("stv") {
                storeFile = file(ciStore)
                storePassword = System.getenv("STV_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("STV_KEY_ALIAS")
                keyPassword = System.getenv("STV_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            if (ciStore != null) signingConfig = signingConfigs.getByName("stv")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}") }
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    // FFmpeg audio decoders (Dolby, DTS) for sounds the device cannot play; built for the same media3 version.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.8")
}