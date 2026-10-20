import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Optional: a Gemini key in local.properties (git-ignored) is used as the default
// for debug builds only. Release builds never contain a key; users enter it in Settings.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val debugGeminiKey: String = (localProps.getProperty("GEMINI_API_KEY") ?: System.getenv("GEMINI_API_KEY") ?: "").trim()

android {
    namespace = "com.techpath.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.techpath.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "DEFAULT_GEMINI_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_TEXT_MODEL", "\"gemini-3.8-flash\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_GEMINI_KEY", "\"$debugGeminiKey\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sign with the debug key so `assembleRelease` is installable for the study.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
