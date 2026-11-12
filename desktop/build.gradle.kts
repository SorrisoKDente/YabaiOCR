plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
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
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation("com.github.kwhat:jnativehook:2.2.2")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("com.github.hypfvieh:dbus-java-core:5.1.1")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.1")
    implementation(libs.onnxruntime)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // Folder holding the PP-OCR files, for the desktop engine's end-to-end test.
    // Enable with:  gradle :desktop:test -Pppocr="C:\path\to\home"  (home/ppocr_v6 holds the 3 files)
    (project.findProperty("ppocr") as String?)?.let { systemProperty("yabai.ppocr", it) }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

val isWindowsHost = System.getProperty("os.name").lowercase().contains("win")
val isLinuxHost = System.getProperty("os.name").lowercase().contains("linux")

// Version stamped into the packages. The release workflow passes `-PyabaiVersion=<tag without v>` so
// an installer, a portable zip and an AppImage from the same tag all carry the same number.
val appVersion = (project.findProperty("yabaiVersion") as String?)?.removePrefix("v") ?: "1.0.0"

compose.desktop {
    application {
        mainClass = "dev.yabai.ocr.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")
        nativeDistributions {
            outputBaseDir.set(layout.buildDirectory.dir("distributions"))
            // dbus-java's native unix-socket transport reads `jdk.net:ExtendedSocketOptions.SO_PEERCRED`.
            // Without this module the jlink runtime image throws NoClassDefFoundError on Linux and the
            // D-Bus service, the KWin bridge and the portal shortcuts all silently fail.
            modules("jdk.net")
            // dbus-java SASL calls `com.sun.security.auth.module.UnixSystem` to get the uid for
            // EXTERNAL authentication. It lives in `jdk.security.auth`, not `java.base`, so a jlink
            // image without it fails before the connection reaches the bus.
            modules("jdk.security.auth")
            appResourcesRootDir.set(
                project.file(if (isWindowsHost) "package/windows" else "package/linux")
            )
            targetFormats(
                *if (isWindowsHost) {
                    arrayOf(
                        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                    )
                } else {
                    arrayOf(
                        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                        org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage,
                    )
                }
            )
            // Linux package names must be lowercase and space-free; the visible name stays "Yabai OCR".
            packageName = if (isWindowsHost) "Yabai OCR" else "yabai-ocr"
            packageVersion = appVersion
            if (isWindowsHost) {
                windows {
                    iconFile.set(project.file("src/main/resources/ic_scan_windows.ico"))
                    shortcut = false
                    menu = true
                    dirChooser = true
                    upgradeUuid = "d7b93a8e-4f12-4c2a-9e81-2a6b4d3f5e01"
                }
            }
            if (isLinuxHost) {
                linux {
                    iconFile.set(project.file("src/main/resources/ic_scan_linux.png"))
                    appCategory = "Utility"
                    menuGroup = "Utility"
                    debMaintainer = "Yabai OCR <yabai-ocr@users.noreply.github.com>"
                    shortcut = true
                }
            }
        }
    }
}

if (isWindowsHost) {
    tasks.register<Exec>("packageInnoSetup") {
        dependsOn("createDistributable")
        group = "compose desktop"
        description = "Packs the desktop app into an Inno Setup installer with optional desktop shortcut and launch on finish checkboxes."

        val userHome = System.getProperty("user.home")
        val isccPath = listOf(
            "$userHome\\AppData\\Local\\Programs\\Inno Setup 6\\ISCC.exe",
            "C:\\Program Files (x86)\\Inno Setup 6\\ISCC.exe",
            "C:\\Program Files\\Inno Setup 6\\ISCC.exe"
        ).firstOrNull { File(it).exists() } ?: "iscc"

        workingDir = project.file("package/windows")
        commandLine = listOf(isccPath, "/DMyAppVersion=$appVersion", "installer.iss")
    }

    tasks.register<Zip>("packagePortableZip") {
        dependsOn("createDistributable")
        group = "compose desktop"
        description = "Compresses the standalone portable application folder into a zip archive."

        archiveFileName.set("Yabai_OCR_Portable_${appVersion}.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions/portable"))
        from(layout.buildDirectory.dir("distributions/main/app/Yabai OCR"))
    }
}

if (isLinuxHost) {
    tasks.register<Tar>("packagePortableTarGz") {
        dependsOn("createDistributable")
        group = "compose desktop"
        description = "Packs the standalone portable Linux application into a tar.gz archive."

        archiveFileName.set("Yabai_OCR_Linux_x86_64_${appVersion}.tar.gz")
        destinationDirectory.set(layout.buildDirectory.dir("distributions/portable"))
        from(layout.buildDirectory.dir("distributions/main/app/yabai-ocr"))
        compression = Compression.GZIP
    }

    // Compose 1.7 maps TargetFormat.AppImage to `jpackage --type app-image`, which produces a
    // directory, not the single-file AppImage format. This task wraps that directory with a real
    // AppRun/desktop entry and calls appimagetool, which the user must have installed.
    tasks.register<Exec>("packageLinuxAppImage") {
        dependsOn("createDistributable")
        group = "compose desktop"
        description = "Builds a single-file .AppImage with appimagetool (requires appimagetool on PATH or APPIMAGETOOL)."

        workingDir = project.file("package/linux")
        environment("YABAI_VERSION", appVersion)
        commandLine("bash", "build-appimage.sh")
    }
}


