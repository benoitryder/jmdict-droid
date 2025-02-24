plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.kotlin.android)
    kotlin("plugin.serialization") version "1.9.22"
}

android {
    namespace = "fr.ryder.benoit.jmdictdroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.ryder.benoit.jmdictdroid"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
            manifestPlaceholders["appRoundIcon"] = "@mipmap/ic_launcher_round"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher_debug"
            manifestPlaceholders["appRoundIcon"] = "@mipmap/ic_launcher_round_debug"
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.icons)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.kotlinx.serialization.json)
}
