package dev.yabai.ocr.core.lens

import dev.yabai.ocr.core.proto.ProtoReader
import dev.yabai.ocr.core.proto.ProtoWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LensProtoParsingTest {

    private fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }

    /**
     * Regression test against bytes captured from the real endpoint (via chrome-lens-ocr 4.1.1).
     * If Google changes the response schema, this fails loudly.
     */
    @Test
    fun `parses the recorded japanese response`() {
        val response = resource("lens_response.bin")
        val parsed = LensProto.parseResponse(response, imageWidth = 800, imageHeight = 1000)
        val built = LensResultBuilder.build(parsed, 800, 1000)

        assertEquals("ja", built.language)
        assertEquals(
            listOf(
                "淀みなき拳で",
                "淀",
                "アニメ第3期第2クール2027年放送決定",
                "STORY by ONE & ART by YUSUKE MUR.",
                "29巻 きっとまいく!",
            ),
            built.segments.map { it.text },
        )

        // Every line has to belong to exactly one tappable block, in order, or the picker would
        // offer something the result panel cannot reproduce.
        assertEquals(built.segments.size, built.blocks.sumOf { it.lines.size })
        assertEquals(
            built.segments.map { it.text },
            built.blocks.flatMap { it.lines },
        )
    }

    /**
     * The recorded cover has two captions stacked at the bottom; Lens reports them as two lines and
     * they are close and aligned enough to be one tappable block. The three far-apart vertical texts
     * above them must stay apart: a box that swallows the whole page is not a picker.
     */
    @Test
    fun `joins only the lines that belong together`() {
        val response = resource("lens_response.bin")
        val parsed = LensProto.parseResponse(response, 800, 1000)
        val built = LensResultBuilder.build(parsed, 800, 1000)

        assertEquals(
            listOf(
                "淀みなき拳で",
                "淀",
                "アニメ第3期第2クール2027年放送決定",
                "STORY by ONE & ART by YUSUKE MUR. 29巻 きっとまいく!",
            ),
            built.blocks.map { it.text },
        )
    }

    /**
     * The shape a real manga balloon arrives in: Lens reports one block per column of vertical text,
     * and the columns of one balloon have to come back as a single block, or tapping it would only
     * translate a third of the sentence.
     *
     * Boxes and gaps measured from a live response for a three-column balloon.
     */
    @Test
    fun `merges the columns of a vertical balloon into one block`() {
        // "おまえは" / "もう" / "死んでいる", read right to left, plus a separate two-column balloon.
        val columns = listOf(
            box(349, 223, 40, 165) to "おまえは",
            box(304, 227, 29, 81) to "もう",
            box(250, 229, 37, 203) to "死んでいる",
            box(770, 883, 37, 124) to "なんて",
            box(721, 889, 34, 120) to "こった",
        )

        val blocks = blocksOf(columns)

        assertEquals(
            listOf("おまえは もう 死んでいる", "なんて こった"),
            blocks.map { it.text },
        )
        // The union of the three columns, not just the first of them.
        assertEquals(box(250, 223, 139, 209), blocks[0].box)
        assertEquals(box(721, 883, 86, 126), blocks[1].box)
        // The columns themselves, in reading order and with their boxes: what the picker needs to
        // draw a reading beside the characters it belongs to instead of beside the balloon.
        assertEquals(
            listOf(
                "おまえは" to box(349, 223, 40, 165),
                "もう" to box(304, 227, 29, 81),
                "死んでいる" to box(250, 229, 37, 203),
            ),
            blocks[0].pieces.map { it.text to it.box },
        )
    }

    /**
     * B1: Lens reports the columns of a vertical balloon in its own order, not in the order they are
     * read. The boxes are what says the order: right to left.
     *
     * The same balloon as above, handed over middle-first.
     */
    @Test
    fun `puts the columns of a vertical balloon in reading order`() {
        val columns = listOf(
            box(304, 227, 29, 81) to "もう",
            box(349, 223, 40, 165) to "おまえは",
            box(250, 229, 37, 203) to "死んでいる",
        )

        assertEquals(
            listOf("おまえは もう 死んでいる"),
            blocksOf(columns).map { it.text },
        )
    }

    /**
     * B1: a column the recogniser split in two pieces — the tails of 「潰されてる」 arriving as
     * 「てる!」 and 「潰され」, bottom half first — is one column, read top to bottom.
     */
    @Test
    fun `puts the two halves of a split column back in order`() {
        val lines = listOf(
            box(301, 290, 36, 70) to "てる!",
            box(300, 200, 36, 84) to "潰され",
            box(349, 200, 36, 160) to "内塊に",
        )

        assertEquals(
            listOf("内塊に 潰され てる!"),
            blocksOf(lines).map { it.text },
        )
    }

    /** B1: horizontal text is rows, top to bottom, whatever order the rows arrive in. */
    @Test
    fun `puts the rows of a horizontal paragraph in reading order`() {
        val rows = listOf(
            box(100, 250, 220, 34) to "二行目",
            box(100, 200, 220, 34) to "一行目",
        )

        assertEquals(
            listOf("一行目 二行目"),
            blocksOf(rows).map { it.text },
        )
    }

    /** Two balloons side by side must not be dragged into one box. */
    @Test
    fun `keeps balloons separated by a gutter apart`() {
        val columns = listOf(
            box(100, 200, 36, 180) to "これ",
            box(700, 200, 36, 180) to "それ",
        )

        val blocks = blocksOf(columns)

        assertEquals(listOf("これ", "それ"), blocks.map { it.text })
    }

    /** A horizontal paragraph and a vertical balloon at the same height must not merge. */
    @Test
    fun `does not mix horizontal text with vertical columns`() {
        val lines = listOf(
            box(100, 200, 36, 180) to "たて",
            box(150, 200, 220, 34) to "よこ",
        )

        val blocks = blocksOf(lines)

        assertEquals(listOf("たて", "よこ"), blocks.map { it.text })
    }

    /** Builds only the blocks, from boxes rather than from protobuf bytes. */
    /**
     * A reading the page prints beside a kanji is part of the raw lines but not of the block's
     * pieces: the picker draws the reading it knows from the dictionary, and drawing the printed one
     * as if it were text would put a column of kana where the sentence is.
     */
    @Test
    fun `a printed reading is not a piece of the block`() {
        // 職員 with しょくいん in the gutter to its right: 40% as thick, 6 px away.
        val blocks = blocksOf(
            listOf(
                box(300, 200, 40, 200) to "職員",
                box(346, 200, 16, 92) to "しょくいん",
            ),
        )

        assertEquals(listOf("職員"), blocks.map { it.text })
        assertEquals(listOf("職員"), blocks.single().pieces.map { it.text })
        assertTrue(
            "the raw lines still show what the recogniser saw",
            blocks.single().lines.contains("しょくいん"),
        )
    }

    private fun blocksOf(lines: List<Pair<PixelBox, String>>): List<LensBlock> =
        LensResultBuilder.groupIntoBlocks(lines.map { LensResultBuilder.Line(it.second, it.first) })

    private fun box(x: Int, y: Int, width: Int, height: Int) = PixelBox(x, y, width, height)

    /**
     * The picker draws these boxes on top of the live screen, so they have to be real: inside the
     * image, non-empty, and enclosing what the block says.
     */
    @Test
    fun `paragraph blocks get a tap target inside the image`() {
        val response = resource("lens_response.bin")
        val parsed = LensProto.parseResponse(response, 800, 1000)
        val built = LensResultBuilder.build(parsed, 800, 1000)

        assertTrue("the recorded response should have blocks", built.blocks.isNotEmpty())
        for (block in built.blocks) {
            val box = block.box
            assertNotNull("block '${block.text}' has no box", box)
            assertTrue("block '${block.text}' is empty", box!!.width > 0 && box.height > 0)
            assertTrue("block '${block.text}' starts off-image", box.x >= 0 && box.y >= 0)
            assertTrue("block '${block.text}' runs past the image", box.right <= 800 && box.bottom <= 1000)
        }
        for (segment in built.segments) {
            val box = segment.box!!
            assertTrue("line '${segment.text}' starts off-image", box.x >= 0 && box.y >= 0)
            assertTrue("line '${segment.text}' runs past the image", box.right <= 800 && box.bottom <= 1000)
        }
    }

    @Test
    fun `bounding boxes are translated to pixels`() {
        val response = resource("lens_response.bin")
        val parsed = LensProto.parseResponse(response, 800, 1000)
        val built = LensResultBuilder.build(parsed, 800, 1000)

        val kanji = built.segments.first { it.text == "淀" }
        val box = kanji.box
        assertNotNull(box)
        assertEquals(53, box!!.x)
        assertEquals(90, box.y)
        assertEquals(129, box.width)
        assertEquals(102, box.height)
    }

    @Test
    fun `handles an empty payload without throwing`() {
        val parsed = LensProto.parseResponse(ByteArray(0), 100, 100)
        val built = LensResultBuilder.build(parsed, 100, 100)
        assertEquals("", built.language)
        assertTrue(built.segments.isEmpty())
        assertTrue(built.blocks.isEmpty())
    }

    /** The request we emit must decode back into the same field numbers the server expects. */
    @Test
    fun `request layout matches the reference wire format`() {
        val image = EncodedImage(ByteArray(16) { 0x42 }, 800, 1000)
        val bytes = LensProto.buildRequest(image, uuid = 1234567890123456789L)

        val serverRequest = ProtoReader(bytes)
        val tag = serverRequest.nextTag()
        assertNotNull(tag)
        assertEquals(1, tag!!.field)
        assertEquals(ProtoWriter.WIRE_LENGTH_DELIMITED, tag.wireType)

        val objectsRequest = ProtoReader(serverRequest.readBytes())
        val fields = mutableListOf<Pair<Int, Int>>()
        while (true) {
            val t = objectsRequest.nextTag() ?: break
            fields += t.field to t.wireType
            objectsRequest.skip(t.wireType)
        }
        // 1 = request_context, 3 = image_data
        assertEquals(listOf(1 to 2, 3 to 2), fields)
    }

    @Test
    fun `encoded request can be decoded back into the original values`() {
        val uuid = 1792286730498466816L
        val image = EncodedImage(ByteArray(8), 320, 240)
        val bytes = LensProto.buildRequest(image, uuid = uuid, language = "ja")

        // Walk: server_request(1) -> objects_request {1: ctx, 3: image_data}
        val root = ProtoReader(bytes)
        val rootTag = root.nextTag()!!
        assertEquals(1, rootTag.field)
        val objects = ProtoReader(root.readBytes())

        var requestContext: ByteArray? = null
        var imageData: ByteArray? = null
        while (true) {
            val t = objects.nextTag() ?: break
            when (t.field) {
                1 -> requestContext = objects.readBytes()
                3 -> imageData = objects.readBytes()
                else -> objects.skip(t.wireType)
            }
        }

        // context {3: request_id, 4: client_context}
        val ctx = ProtoReader(requestContext!!)
        var requestId: ByteArray? = null
        var clientContext: ByteArray? = null
        while (true) {
            val t = ctx.nextTag() ?: break
            when (t.field) {
                3 -> requestId = ctx.readBytes()
                4 -> clientContext = ctx.readBytes()
                else -> ctx.skip(t.wireType)
            }
        }

        val idReader = ProtoReader(requestId!!)
        val idTag = idReader.nextTag()!!
        assertEquals(1, idTag.field)
        assertEquals(ProtoWriter.WIRE_VARINT, idTag.wireType)
        assertEquals(uuid, idReader.readVarint())

        // client_context {4: locale, 17: applied filters}
        val ccReader = ProtoReader(clientContext!!)
        var locale: ByteArray? = null
        var filters: ByteArray? = null
        while (true) {
            val t = ccReader.nextTag() ?: break
            when (t.field) {
                4 -> locale = ccReader.readBytes()
                17 -> filters = ccReader.readBytes()
                else -> ccReader.skip(t.wireType)
            }
        }
        val localeReader = ProtoReader(locale!!)
        val localeTag = localeReader.nextTag()!!
        assertEquals(1, localeTag.field)
        assertEquals("ja", localeReader.readString())

        val filtersReader = ProtoReader(filters!!)
        val filterTag = filtersReader.nextTag()!!
        assertEquals(1, filterTag.field)
        val filterReader = ProtoReader(filtersReader.readBytes())
        val filterTypeTag = filterReader.nextTag()!!
        assertEquals(1, filterTypeTag.field)
        assertEquals(LensProto.FILTER_AUTO, filterReader.readVarint().toInt())

        // image_data {1: payload{1: bytes}, 3: metadata{1: w, 2: h}}
        val idr = ProtoReader(imageData!!)
        var payload: ByteArray? = null
        var metadata: ByteArray? = null
        while (true) {
            val t = idr.nextTag() ?: break
            when (t.field) {
                1 -> payload = idr.readBytes()
                3 -> metadata = idr.readBytes()
                else -> idr.skip(t.wireType)
            }
        }
        val payloadReader = ProtoReader(payload!!)
        assertEquals(1, payloadReader.nextTag()!!.field)
        assertEquals(8, payloadReader.readBytes().size)

        val metaReader = ProtoReader(metadata!!)
        assertEquals(1, metaReader.nextTag()!!.field)
        assertEquals(320, metaReader.readVarint().toInt())
        assertEquals(2, metaReader.nextTag()!!.field)
        assertEquals(240, metaReader.readVarint().toInt())
    }
}
