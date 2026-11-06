plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.yabai.ocr"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.yabai.ocr"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            // The bundled ML Kit model ships native code for four ABIs and that is most of the
            // APK. Phones are ARM, x86/x86_64 only matter for emulators, and the 32-bit ARM slice
            // is only needed for phones older than 2016: keeping just arm64-v8a removes about
            // 18 MB. Add "armeabi-v7a" back if you need to install on a 32-bit device.
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Signed with the debug keystore so `assembleRelease` produces an installable APK
            // without extra setup. Replace with your own keystore before publishing.
            signingConfig = signingConfigs.getByName("debug")
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        viewBinding = false
    }

    testOptions {
        // The overlay layouts are built in code and measured in Robolectric tests, which need the
        // real resources (strings, colours, themes) to resolve.
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2048m"
                // Robolectric generates lambda classes with ASM while inflating animated widgets.
                // The default worker stack runs out on the full suite and surfaces as a bogus
                // StackOverflowError in an unrelated test.
                it.jvmArgs("-Xss4m")
            }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.google.material)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // On-device Japanese OCR: bundled model, works with no network and no Play Services.
    implementation(libs.mlkit.text.recognition.japanese)

    // On-device translation once the language model has been fetched (offline from then on).
    implementation(libs.mlkit.translate)

    implementation(libs.okhttp)

    // Periodic dictionary-update checks that survive reboots and follow the network constraint.
    implementation(libs.androidx.work.runtime.ktx)

    // Runs the overlay views on the JVM: the panels are built in code, so the only way to check a
    // layout without a device is to measure it here.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
}
