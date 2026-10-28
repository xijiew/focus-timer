import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.xijie.focustimer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xijie.focustimer"
        minSdk = 29
        targetSdk = 36
        versionCode = 18
        versionName = "2.7"
    }

    /**
     * 固定签名（解决"每次装的 APK 签名不同、不能覆盖升级"）。
     * CI 每次都是全新虚拟机，AGP 自动生成的 debug.keystore 每次都不一样，
     * 所以把 keystore.p12 放进仓库、始终用它签名。
     * 这只是自用 debug 签名，与任何账号凭据无关。
     */
    signingConfigs {
        create("fixed") {
            storeFile = rootProject.file("keystore.p12")
            storePassword = "focus-timer-2026"
            keyAlias = "focus-timer"
            keyPassword = "focus-timer-2026"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

/**
 * 护栏：compileSdk 36 / AGP 8.13.2 世代的天花板。
 * androidx 有些库（如 lifecycle 2.11、core 1.19）要求 compileSdk 37 + AGP 9.1，
 * 一旦被传递依赖"对齐"抬高就会在 checkDebugAarMetadata 阶段失败，这里强制钉住。
 */
configurations.configureEach {
    resolutionStrategy {
        force(
            "androidx.core:core:1.18.0",
            "androidx.core:core-ktx:1.18.0",
            "androidx.lifecycle:lifecycle-runtime:2.10.0",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.10.0",
            "androidx.lifecycle:lifecycle-runtime-compose:2.10.0",
            "androidx.lifecycle:lifecycle-viewmodel:2.10.0",
            "androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0"
        )
    }
}
