package dev.yabai.ocr.core

import org.junit.Assert.assertTrue
import org.junit.Test

class DonationInfoTest {

    @Test
    fun `the pix key is a plain email, which is what banks accept`() {
        val key = DonationInfo.PIX_KEY

        assertTrue("the key must not carry spaces", key.none { it.isWhitespace() })
        assertTrue("the key must look like name@domain", Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""").matches(key))
    }

    @Test
    fun `the kofi link is https and points at the project page`() {
        assertTrue(DonationInfo.KOFI_URL.startsWith("https://ko-fi.com/"))
        assertTrue(DonationInfo.KOFI_URL.endsWith("/yabaihonyaku"))
    }
}
