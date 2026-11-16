plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.compose") version "1.12.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
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
    // `Dispatchers.Main` only exists when an artifact installs a main dispatcher. On desktop that
    // is the Swing one, which posts to the AWT event thread — the same thread Compose renders on.
    // Without it `withContext(Dispatchers.Main)` throws "Module with the Main dispatcher is missing"
    // and the update check dies in a worker thread.
    implementation(libs.kotlinx.coroutines.swing)
    implementation("com.github.kwhat:jnativehook:2.2.2")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("com.github.hypfvieh:dbus-java-core:5.1.1")
    // KWin's ScreenShot2 passes the image through a Unix file descriptor. The JDK's native
    // unix-domain transport cannot send SCM_RIGHTS, so use the junixsocket transport, which
    // implements dbus-java's file-descriptor provider.
    implementation("com.github.hypfvieh:dbus-java-transport-junixsocket:5.1.1")
    implementation(libs.onnxruntime)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // `TrayGlyphsTest` reads the Inno script and the packaged executable name. Gradle cannot see
    // those reads, so without this a change to the installer leaves the test task up to date (or
    // restores it from the build cache) and the assertions never run against the new file.
    inputs.file(layout.projectDirectory.file("package/windows/installer.iss"))
        .withPropertyName("windowsInstallerScript")
        .withPathSensitivity(PathSensitivity.RELATIVE)
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
val appVersion = (project.findProperty("yabaiVersion") as String?)?.trim()?.removePrefix("v")
    ?.takeIf { it.isNotEmpty() } ?: "1.0.9"

// The Windows installer accepts only `MAJOR.MINOR.BUILD` (255.255.65535) and fails configuration
// with a trailing `$env:VERSION`-style string or a pre-release suffix. Normalising here keeps the
// display version intact and gives a message that names the offending value.
val installerPackageVersion: String = run {
    val core = appVersion.substringBefore('-').substringBefore('+')
    val parts = core.split('.')
    require(parts.size in 1..3 && parts.all { part -> part.isNotEmpty() && part.all(Char::isDigit) }) {
        "yabaiVersion must be MAJOR.MINOR.BUILD after the leading v (got '$appVersion')"
    }
    (parts + List(3 - parts.size) { "0" }).joinToString(".")
}

// The About card and the update checker must report the version that was packaged. They used to
// print a hardcoded "1.0.0", so a build from another tag compared itself against the wrong number
// and the update check never offered the release it was already ahead of.
val versionResourceDir = layout.buildDirectory.dir("generated/yabai-version")
val generateVersionResource = tasks.register("generateVersionResource") {
    val version = appVersion
    val outputDir = versionResourceDir
    inputs.property("appVersion", version)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("yabai-version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$version\n")
    }
}

sourceSets.named("main") { resources.srcDir(versionResourceDir) }
tasks.named("processResources") { dependsOn(generateVersionResource) }

// The license has to travel with the software: PolyForm requires that whoever gets a copy also gets
// its terms. One source of truth (the repository LICENSE) reaches two places — the jar, so the app
// can show it in Settings → About, and the distributable folder, which every packager (Inno Setup,
// portable zip, tar.gz, AppImage) reuses.
val licenseResourceDir = layout.buildDirectory.dir("generated/yabai-license")
val generateLicenseResource = tasks.register<Copy>("generateLicenseResource") {
    from(rootProject.file("LICENSE")) { rename { "LICENSE.txt" } }
    into(licenseResourceDir)
}

sourceSets.named("main") { resources.srcDir(licenseResourceDir) }
tasks.named("processResources") { dependsOn(generateLicenseResource) }

compose.desktop {
    application {
        mainClass = "dev.yabai.ocr.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")
        // CMP-9994 / JDK-8379560: on Ubuntu 24.04+ a native library that formats through
        // std::ostream (PP-OCR's ONNX Runtime, for instance) crashes the JVM inside libstdc++
        // when Compose has configured the Swing/AWT globals. Compose 1.10 fixed the root cause
        // (this project is on 1.12.2), so the flag is only a belt-and-braces switch now: it costs
        // nothing and it already shipped in 1.0.7. Kept on Linux only, so Windows is unchanged.
        if (isLinuxHost) {
            jvmArgs += "-Dcompose.application.configure.swing.globals=false"
        }
        nativeDistributions {
            outputBaseDir.set(layout.buildDirectory.dir("distributions"))
            // Keep jdk.net in the packaged image: it was required by the earlier native D-Bus
            // transport and the Linux socket stack is shared by the D-Bus service, the KWin bridge
            // and the portal shortcuts. The FD path itself now goes through junixsocket.
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
            packageVersion = installerPackageVersion
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

// jpackage writes the app folder with a platform-specific name; the packagers below archive that
// folder, so the license only has to be copied there once.
val distributableAppDir = layout.buildDirectory.dir(
    if (isWindowsHost) "distributions/main/app/Yabai OCR" else "distributions/main/app/yabai-ocr"
)
val copyLicenseToDistributable = tasks.register<Copy>("copyLicenseToDistributable") {
    dependsOn("createDistributable")
    from(rootProject.file("LICENSE"))
    into(distributableAppDir)
}

if (isWindowsHost) {
    tasks.register<Exec>("packageInnoSetup") {
        dependsOn("createDistributable", "copyLicenseToDistributable")
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
        dependsOn("createDistributable", "copyLicenseToDistributable")
        group = "compose desktop"
        description = "Compresses the standalone portable application folder into a zip archive."

        archiveFileName.set("Yabai_OCR_Portable_${appVersion}.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions/portable"))
        from(layout.buildDirectory.dir("distributions/main/app/Yabai OCR"))
    }
}

if (isLinuxHost) {
    tasks.register<Tar>("packagePortableTarGz") {
        dependsOn("createDistributable", "copyLicenseToDistributable")
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
        dependsOn("createDistributable", "copyLicenseToDistributable")
        group = "compose desktop"
        description = "Builds a single-file .AppImage with appimagetool (requires appimagetool on PATH or APPIMAGETOOL)."

        workingDir = project.file("package/linux")
        environment("YABAI_VERSION", appVersion)
        commandLine("bash", "build-appimage.sh")
    }
}


