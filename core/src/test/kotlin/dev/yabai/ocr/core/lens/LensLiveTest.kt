package dev.yabai.ocr.core.lens

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Talks to the real endpoint. Skipped by default; run with `gradle :core:test -Plive=1`.
 *
 * This is the test that proves the hand-written protobuf encoder is accepted by Google, which the
 * offline fixtures alone cannot show.
 */
class LensLiveTest {

    private fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }

    @Test
    fun `live lens reads vertical japanese including the kanji 淀`() {
        assumeTrue("set -Plive=1 to enable", System.getProperty("yabai.live") == "1")

        val png = resource("test_jp.png")
        val image = EncodedImage(png, width = 800, height = 1000)

        LensClient().use { client ->
            val result = client.recognize(image, LensOptions(language = "ja"))
            println("language=${result.language} elapsedMs=${result.elapsedMs}")
            result.segments.forEach { println("  ${it.box} ${it.text}") }

            val text = result.joinLines()
            assertTrue("expected the kanji 淀 in: $text", text.contains("淀"))
            assertTrue("expected the vertical line in: $text", text.contains("淀みなき拳で"))
        }
    }

    @Test
    fun `live lens accepts the platform and surface fields too`() {
        assumeTrue("set -Plive=1 to enable", System.getProperty("yabai.live") == "1")

        val png = resource("test_jp.png")
        val image = EncodedImage(png, width = 800, height = 1000)

        LensClient().use { client ->
            val result = client.recognize(
                image,
                LensOptions(language = "ja", includePlatformFields = true),
            )
            println("with platform/surface: language=${result.language}")
            result.segments.forEach { println("  ${it.text}") }
            assertTrue(result.joinLines().contains("淀"))
        }
    }
}
