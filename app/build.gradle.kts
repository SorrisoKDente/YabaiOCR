plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// The license must travel with the app: PolyForm requires that whoever gets a copy also gets its
// terms. The build copies the repository LICENSE into the APK assets, so the app can show it and
// nothing has to be kept in two places.
val generateLicenseAsset = tasks.register<Copy>("generateLicenseAsset") {
    from(rootProject.file("LICENSE"))
    into(layout.buildDirectory.dir("generated/license-assets"))
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

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_FILE") ?: "release.jks"
            val keystoreFile = file(keystorePath)

            if (keystoreFile.exists() && !System.getenv("KEYSTORE_PASSWORD").isNullOrEmpty()) {
                val pass = System.getenv("KEYSTORE_PASSWORD")
                val aliasPass = System.getenv("KEY_PASSWORD")
                storeFile = keystoreFile
                storePassword = pass
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = if (aliasPass.isNullOrEmpty()) pass else aliasPass
            } else {
                storeFile = signingConfigs.getByName("debug").storeFile
                storePassword = signingConfigs.getByName("debug").storePassword
                keyAlias = signingConfigs.getByName("debug").keyAlias
                keyPassword = signingConfigs.getByName("debug").keyPassword
            }
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
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    androidComponents {
        onVariants { variant ->
            val apkName = if (variant.name == "debug") "Yabai OCR-debug.apk" else "Yabai OCR.apk"
            variant.outputs.forEach { output ->
                (output as com.android.build.api.variant.impl.VariantOutputImpl).outputFileName.set(apkName)
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        buildConfig = true
        viewBinding = false
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(layout.buildDirectory.dir("generated/license-assets"))
        }
    }

    testOptions {
        // The overlay layouts are built in code and measured in Robolectric tests, which need the
        // real resources (strings, colours, themes) to resolve.
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3072m"
                it.jvmArgs("-Xss8m")
                it.forkEvery = 100
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

    implementation(libs.onnxruntime.android)

    implementation(libs.okhttp)

    // Periodic dictionary-update checks that survive reboots and follow the network constraint.
    implementation(libs.androidx.work.runtime.ktx)

    // Runs the overlay views on the JVM: the panels are built in code, so the only way to check a
    // layout without a device is to measure it here.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
}

tasks.named("preBuild") {
    dependsOn(generateLicenseAsset)
}

val renameReleaseApk = tasks.register<Copy>("renameReleaseApk") {
    from("build/outputs/apk/release/Yabai OCR.apk")
    into(rootProject.file("apk-output"))
}

val renameDebugApk = tasks.register<Copy>("renameDebugApk") {
    from("build/outputs/apk/debug/Yabai OCR-debug.apk")
    into(rootProject.file("apk-output"))
}

afterEvaluate {
    tasks.named("assembleRelease") {
        finalizedBy(renameReleaseApk)
    }
    tasks.named("assembleDebug") {
        finalizedBy(renameDebugApk)
    }
}
