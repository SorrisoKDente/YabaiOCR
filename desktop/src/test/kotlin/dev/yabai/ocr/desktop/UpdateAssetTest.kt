package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateAssetTest {

    private val assets = listOf(
        ReleaseAsset("Yabai-OCR-1.2.0.AppImage", "https://example.test/yabai.appimage"),
        ReleaseAsset("Yabai-OCR-Setup-1.2.0.exe", "https://example.test/yabai.exe"),
        ReleaseAsset("Yabai-OCR-1.2.0.msi", "https://example.test/yabai.msi"),
        ReleaseAsset("Yabai-OCR-1.2.0.dmg", "https://example.test/yabai.dmg"),
    )

    @Test
    fun `linux downloads the appimage`() {
        assertEquals(
            "https://example.test/yabai.appimage",
            preferredReleaseAsset(OsFamily.LINUX, assets)?.downloadUrl,
        )
    }

    @Test
    fun `windows prefers the exe over the msi`() {
        assertEquals(
            "https://example.test/yabai.exe",
            preferredReleaseAsset(OsFamily.WINDOWS, assets)?.downloadUrl,
        )
    }

    @Test
    fun `windows falls back to the msi`() {
        val onlyMsi = assets.filterNot { it.name.endsWith(".exe") }

        assertEquals(
            "https://example.test/yabai.msi",
            preferredReleaseAsset(OsFamily.WINDOWS, onlyMsi)?.downloadUrl,
        )
    }

    @Test
    fun `macos downloads the dmg`() {
        assertEquals(
            "https://example.test/yabai.dmg",
            preferredReleaseAsset(OsFamily.MACOS, assets)?.downloadUrl,
        )
    }

    @Test
    fun `a release without a matching asset falls back to the release page`() {
        val onlyLinux = assets.filter { it.name.endsWith(".AppImage") }

        assertNull(preferredReleaseAsset(OsFamily.WINDOWS, onlyLinux))
        assertNull(preferredReleaseAsset(OsFamily.OTHER, assets))
        assertNull(preferredReleaseAsset(OsFamily.LINUX, emptyList()))
    }

    @Test
    fun `the extension match is case insensitive`() {
        val upper = listOf(ReleaseAsset("YABAI.APPIMAGE", "https://example.test/upper"))

        assertEquals(
            "https://example.test/upper",
            preferredReleaseAsset(OsFamily.LINUX, upper)?.downloadUrl,
        )
    }
}
