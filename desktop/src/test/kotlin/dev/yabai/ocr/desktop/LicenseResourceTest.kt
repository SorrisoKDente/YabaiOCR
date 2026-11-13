package dev.yabai.ocr.desktop

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The license has to be inside the build, not only in the repository.
 *
 * PolyForm requires that anyone who gets a copy also gets its terms, and the About window shows this
 * text, so a missing resource would be both a license violation and a broken button.
 */
class LicenseResourceTest {

    @Test
    fun `the packaged license is the project license`() {
        val text = LicenseText.load()

        assertTrue("the license resource is missing from the build", text.isNotEmpty())
        assertTrue(text.contains("PolyForm Noncommercial License 1.0.0"))
        assertTrue(text.contains("Required Notice: Copyright (c) 2026 Yabai OCR contributors"))
        assertTrue("the license must be the full text", text.contains("## Definitions"))
    }
}
