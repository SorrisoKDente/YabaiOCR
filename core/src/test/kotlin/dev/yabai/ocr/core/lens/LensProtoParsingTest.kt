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
        val (language, segments) = LensResultBuilder.build(parsed, 800, 1000)

        assertEquals("ja", language)
        assertEquals(
            listOf(
                "淀みなき拳で",
                "淀",
                "アニメ第3期第2クール2027年放送決定",
                "STORY by ONE & ART by YUSUKE MUR.",
                "29巻 きっとまいく!",
            ),
            segments.map { it.text },
        )
    }

    @Test
    fun `bounding boxes are translated to pixels`() {
        val response = resource("lens_response.bin")
        val parsed = LensProto.parseResponse(response, 800, 1000)
        val (_, segments) = LensResultBuilder.build(parsed, 800, 1000)

        val kanji = segments.first { it.text == "淀" }
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
        val (language, segments) = LensResultBuilder.build(parsed, 100, 100)
        assertEquals("", language)
        assertTrue(segments.isEmpty())
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
