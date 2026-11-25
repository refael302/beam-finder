plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.refael302.beamfinder"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.refael302.beamfinder"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.1.7"
    }

    // Stable key for GitHub sideload OTA. CI debug keystores are ephemeral per runner,
    // so unsigned-with-default-debug builds cannot update each other.
    signingConfigs {
        create("github") {
            storeFile = file("signing/github-distribution.keystore")
            storePassword = "beamfinder-github-ota"
            keyAlias = "beamfinder"
            keyPassword = "beamfinder-github-ota"
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"github\"")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"play\"")
        }
    }

    buildTypes {
        // Build-type signing wins over flavor signing; pin github* variants below.
        debug { }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

kotlin {
    jvmToolchain(17)
}

// Debug buildType's default keystore otherwise overrides the flavor. Force the stable
// github key on every github variant so Release APKs can update each other.
androidComponents {
    onVariants(selector().withFlavor("distribution" to "github")) { variant ->
        val cfg = android.signingConfigs.getByName("github")
        variant.signingConfig.setConfig(cfg)
    }
}

dependencies {
    implementation(project(":detection"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
