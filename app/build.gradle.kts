@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
}

android {
    namespace = "com.remtrik.m3khelper"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.remtrik.m3khelper"
        minSdk = 29
        targetSdk = 36
        versionCode = 67
        versionName = "6.2.0-TDWRA"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            vcsInfo.include = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    kotlin {
        jvmToolchain(21)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "META-INF/*.version"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                val abi = output.filters.find {
                    it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
                }?.identifier

                output.outputFileName.set(
                    "M3K_Helper_v${defaultConfig.versionName}_${defaultConfig.versionCode}-${variant.name}-${abi ?: "all"}.apk"
                )
            }
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Navigation / Destinations
    implementation(libs.compose.destinations.core)
    ksp(libs.compose.destinations.ksp)

    // 🔥 SHIZUKU (corrigido)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // LibSU (root fallback)
    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.nio)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Material
    implementation(libs.material)
    implementation(libs.materialKolor)

    // Network
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
}
