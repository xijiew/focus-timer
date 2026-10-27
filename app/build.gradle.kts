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
        versionCode = 4
        versionName = "1.3"
    }

    buildTypes {
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
