import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is read from signing.properties (not committed). Without it there is no
// installable release build (assembleRelease produces an unsigned APK); use assembleDebug to try the app.
val signingProps = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.lrlr.carmigo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lrlr.carmigo"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // 기록 받아 가기 서버 (운전자가 버튼을 누를 때만 쓴다)
        buildConfigField("String", "BOOK_SERVER", "\"https://lrlr-master.com\"")
        // Pleos Connect Emulator 2.1.0에서 좌석 착석 값이 반대로 온다(원시 VHAL 값은 정상).
        // 이 값은 기본 방향일 뿐이고, 달리기 시작할 때 운전석이 비어 있다고 읽히면 앱이 방향을 바로잡는다.
        buildConfigField("boolean", "INVERT_SEAT_OCCUPANCY", "true")
    }

    signingConfigs {
        if (signingProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }

    // 주행 중 제한(CarUxRestrictions)과 바깥 기온은 플랫폼의 android.car 라이브러리로 읽는다.
    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.location)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    implementation(libs.androidx.splashscreen)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
