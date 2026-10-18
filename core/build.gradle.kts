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
    api(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks.withType<Test>().configureEach {
    // Real dictionaries are large and the compiler holds one key string per reading in memory.
    maxHeapSize = "2g"
    // Opt-in integration test that talks to the real Lens endpoint.
    // Enable with:  gradle :core:test -Plive=1
    if (project.hasProperty("live")) {
        systemProperty("yabai.live", "1")
    }
    // Folder holding real Yomitan dictionary ZIPs, for the dictionary engine tests.
    // Enable with:  gradle :core:test -Pdicts="C:\path\to\zips"
    (project.findProperty("dicts") as String?)?.let { systemProperty("yabai.dicts", it) }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
