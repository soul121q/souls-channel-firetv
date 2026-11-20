plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.soulschannel.firetv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.soulschannel.firetv"
        // Fire OS 7 (Android 9 / API 28) & Fire OS 8 (Android 11 / API 30) — Fire TV Cube Gen 2 & Gen 3
        minSdk = 26
        targetSdk = 34
        versionCode = 25
        versionName = "2.4.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.webkit:webkit:1.11.0")
    // Hardware-accelerated Media3 ExoPlayer for Fire TV Cube HLS/M3U8 & 4K playback
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
