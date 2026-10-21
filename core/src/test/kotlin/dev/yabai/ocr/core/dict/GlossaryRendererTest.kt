package dev.yabai.ocr.core.dict

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The text a Yomitan glossary renders to, and the shape the panel draws.
 *
 * The big fixture is Jitendex's real structure for 呼ばれる, cut down to two senses: the markup keeps
 * the part of speech, the definition, a note, an example sentence and its translation inside the same
 * list item, and an earlier version of this renderer joined them with nothing — `1-danintransitive1.
 * to be called outNote passive form of 呼ぶ` — which is what the user saw on the phone (R19).
 */
class GlossaryRendererTest {

    private fun json(text: String): JsonElement = JsonParser.parseString(text)

    private fun glossary(text: String): JsonArray = json(text).asJsonArray

    private fun render(text: String): GlossaryRenderer.Rendered =
        GlossaryRenderer.renderGlossary(glossary(text))

    private fun definition(text: String): String =
        GlossaryRenderer.renderDefinition(render(text).blocks)

    /** Jitendex, trimmed to two senses: one with a note and an example, one with two glosses. */
    private val jitendex = """
        [
          {"type":"structured-content","content":[
            {"tag":"ul","lang":"ja","data":{"content":"sense-groups"},"content":
              {"tag":"li","data":{"content":"sense-group"},"content":[
                {"tag":"span","data":{"class":"tag","code":"v1","content":"part-of-speech-info"},"content":"1-dan"},
                {"tag":"span","data":{"class":"tag","code":"vi","content":"part-of-speech-info"},"content":"intransitive"},
                {"tag":"ol","content":[
                  {"tag":"li","data":{"content":"sense"},"content":[
                    {"tag":"ul","data":{"content":"glossary"},"content":{"tag":"li","content":"to be called out"}},
                    {"tag":"div","data":{"content":"extra-info"},"content":[
                      {"tag":"div","data":{"class":"extra-box","content":"sense-note"},"content":[
                        {"tag":"div","data":{"class":"extra-label","content":"sense-note-label"},"content":"Note"},
                        {"tag":"div","data":{"class":"extra-content","content":"sense-note-content"},"content":"passive form of 呼ぶ"}
                      ]},
                      {"tag":"div","content":{"tag":"div","data":{"class":"extra-box","content":"example-sentence"},"content":[
                        {"tag":"div","data":{"content":"example-sentence-a"},"content":
                          {"tag":"span","lang":"ja","content":["私の名前が暗闇の中で二度",{"tag":"span","data":{"content":"example-keyword"},"content":"呼ばれる"},"のが聞こえた。"]}},
                        {"tag":"div","data":{"content":"example-sentence-b"},"content":[
                          {"tag":"span","lang":"en","content":"I heard my name called twice in the dark."},
                          {"tag":"span","data":{"content":"attribution-footnote"},"content":"[1]"}
                        ]}
                      ]}}
                    ]}
                  ]},
                  {"tag":"li","data":{"content":"sense"},"content":[
                    {"tag":"ul","data":{"content":"glossary"},"content":[
                      {"tag":"li","content":"to be called (a name)"},
                      {"tag":"li","content":"to be referred to as"}
                    ]}
                  ]}
                ]}
              ]}
            }
          ]}
        ]
    """.trimIndent()

    @Test
    fun `jitendex senses are numbered once and nothing runs together`() {
        assertEquals(
            """
            1. to be called out
            ℹ passive form of 呼ぶ
            例 私の名前が暗闇の中で二度呼ばれるのが聞こえた。
            → I heard my name called twice in the dark.
            2. to be called (a name); to be referred to as
            """.trimIndent(),
            definition(jitendex),
        )
    }

    @Test
    fun `the part of speech is reported apart from the definitions`() {
        assertEquals("1-dan・intransitive", render(jitendex).tags)
        assertFalse(definition(jitendex).contains("1-dan"))
        assertFalse(definition(jitendex).contains("intransitive"))
    }

    @Test
    fun `the tatoeba footnote number is not text the panel needs`() {
        assertFalse(definition(jitendex).contains("[1]"))
    }

    @Test
    fun `a plain string glossary is one numbered sense`() {
        assertEquals("1. to eat", definition("""["to eat"]"""))
    }

    @Test
    fun `two glosses of one sense stay on one line, separated`() {
        val text = """
            [
              {"tag":"ul","data":{"content":"glossary"},"content":[
                {"tag":"li","content":"to eat"},
                {"tag":"li","content":"to consume"}
              ]}
            ]
        """.trimIndent()

        assertEquals("1. to eat; to consume", definition(text))
    }

    @Test
    fun `a note inside a definition gets a line of its own`() {
        val text = """
            [
              {"tag":"ul","content":{"tag":"li","content":[
                {"tag":"span","content":"to eat"},
                {"tag":"div","data":{"content":"note"},"content":"informal"}
              ]}}
            ]
        """.trimIndent()

        assertEquals("1. to eat\n   informal", definition(text))
    }

    @Test
    fun `a list of definitions does not run together`() {
        val text = """
            [
              {"tag":"ul","content":[
                {"tag":"li","content":"first meaning"},
                {"tag":"li","content":"second meaning"}
              ]}
            ]
        """.trimIndent()

        assertEquals("1. first meaning\n   second meaning", definition(text))
    }

    /**
     * Jitendex keeps the Tatoeba attribution beside the entry, inside the same `structured-content`.
     * Treating the wrapper as one node made the whole entry fall back to the plain rendering, which
     * is how the phone still showed `1. 1-dantransitive` after the first fix.
     */
    @Test
    fun `an attribution beside the entry is not part of the definition`() {
        val text = """
            [
              {"type":"structured-content","content":[
                {"tag":"ul","data":{"content":"sense-groups"},"content":[
                  {"tag":"li","data":{"content":"sense-group"},"content":[
                    {"tag":"span","data":{"class":"tag","content":"part-of-speech-info"},"content":"v1"},
                    {"tag":"ol","content":[
                      {"tag":"li","data":{"content":"sense"},"content":[
                        {"tag":"ul","data":{"content":"glossary"},"content":{"tag":"li","content":"to eat"}}
                      ]},
                      {"tag":"li","data":{"content":"sense"},"content":[
                        {"tag":"ul","data":{"content":"glossary"},"content":{"tag":"li","content":"to live on"}}
                      ]}
                    ]}
                  ]}
                ]},
                {"tag":"div","data":{"content":"attribution"},"content":[
                  {"tag":"a","href":"https://www.edrdg.org","content":"JMdict"}, " | Tatoeba "
                ]}
              ]}
            ]
        """.trimIndent()

        val rendered = render(text)

        assertEquals("1. to eat\n2. to live on", GlossaryRenderer.renderDefinition(rendered.blocks))
        assertEquals("v1", rendered.tags)
    }

    @Test
    fun `senses past the limit are not rendered`() {
        val many = (1..5).joinToString(",") { number ->
            """{"tag":"ul","data":{"content":"glossary"},"content":{"tag":"li","content":"sense $number"}}"""
        }
        val rendered = GlossaryRenderer.renderGlossary(glossary("[$many]"), sensesLimit = 2)

        assertEquals(2, rendered.blocks.count { it.numbered })
        assertTrue(rendered.blocks.all { !it.text.contains("sense 3") })
    }
}
