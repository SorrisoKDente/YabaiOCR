plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks.withType<Test>().configureEach {
    // Opt-in integration test that talks to the real Lens endpoint.
    // Enable with:  gradle :core:test -Plive=1
    if (project.hasProperty("live")) {
        systemProperty("yabai.live", "1")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
