package dev.yabai.ocr.core.lens

import dev.yabai.ocr.core.proto.ProtoReader
import dev.yabai.ocr.core.proto.ProtoWriter
import dev.yabai.ocr.core.proto.ProtoWriter.Companion.WIRE_LENGTH_DELIMITED
import dev.yabai.ocr.core.proto.ProtoWriter.Companion.WIRE_VARINT

/**
 * Hand-written mirror of the protobuf messages that Google Lens/Chromium exchanges with
 * `lensfrontend-pa.googleapis.com/v1/crupload`.
 *
 * Field numbers were recovered from the generated bindings published by the `chrome-lens-ocr`
 * project (ISC). Only the fields actually used are declared.
 *
 * ```
 * LensOverlayServerRequest
 *   1: LensOverlayObjectsRequest
 *        1: LensOverlayRequestContext
 *             3: LensOverlayRequestId   { 1: uuid(uint64), 2: sequence_id, 3: image_sequence_id }
 *             4: LensOverlayClientContext
 *                   1: platform(enum)  2: surface(enum)
 *                   4: LocaleContext      { 1: language, 2: region, 3: time_zone }
 *                  17: AppliedFilters     { 1: repeated AppliedFilter { 1: filter_type(enum) } }
 *        3: ImageData
 *             1: ImagePayload  { 1: image_bytes }
 *             3: ImageMetadata { 1: width, 2: height }
 *
 * LensOverlayServerResponse
 *   1: LensOverlayServerError { 1: error_type(enum) }
 *   2: LensOverlayObjectsResponse
 *        3: Text
 *             1: TextLayout { 1: repeated Paragraph
 *                                 1: id { 1: value }
 *                                 2: repeated Line
 *                                      1: repeated Word
 *                                           1: id
 *                                           2: plain_text
 *                                           3: text_separator
 *                                           4: Geometry { 1: CenterRotatedBox
 *                                                            1: center_x(f32) 2: center_y(f32)
 *                                                            3: width(f32)    4: height(f32)
 *                                                            5: rotation(f32) 6: coordinate_type(enum) }
 *                                      2: Geometry
 *                                 3: Geometry
 *                                 4: writing_direction(enum)
 *                                 5: content_language }
 *             2: content_language
 * ```
 */
object LensProto {

    const val ENDPOINT = "https://lensfrontend-pa.googleapis.com/v1/crupload"
    const val API_KEY = "AIzaSyDr2UxVnv_U85AbhhY8XSHSIavUW0DC-sY"

    // --- enums ---------------------------------------------------------------------------------
    const val PLATFORM_WEB = 3
    const val SURFACE_CHROMIUM = 4
    const val FILTER_AUTO = 7
    const val COORDINATE_TYPE_NORMALIZED = 1
    const val WRITING_DIRECTION_TOP_TO_BOTTOM = 2

    private const val SERVER_ERROR_UNKNOWN_TYPE = 0

    /**
     * Builds the `LensOverlayServerRequest` payload for one image.
     *
     * @param uuid 64-bit request id; only needs to be unique per request.
     */
    fun buildRequest(
        image: EncodedImage,
        uuid: Long,
        language: String = "ja",
        region: String = "US",
        timeZone: String = "America/New_York",
        includePlatformFields: Boolean = false,
    ): ByteArray {

        val requestId = ProtoWriter().apply {
            writeUInt64(1, uuid)
            writeInt32(2, 1) // sequence_id
            writeInt32(3, 1) // image_sequence_id
        }.toByteArray()

        val localeContext = ProtoWriter().apply {
            writeString(1, language)
            writeString(2, region)
            writeString(3, timeZone)
        }.toByteArray()

        val appliedFilter = ProtoWriter().apply {
            writeEnum(1, FILTER_AUTO)
        }.toByteArray()

        val appliedFilters = ProtoWriter().apply {
            writeMessage(1, appliedFilter)
        }.toByteArray()

        val clientContext = ProtoWriter().apply {
            if (includePlatformFields) {
                writeEnum(1, PLATFORM_WEB)
                writeEnum(2, SURFACE_CHROMIUM)
            }
            writeMessage(4, localeContext)
            writeMessage(17, appliedFilters)
        }.toByteArray()

        val requestContext = ProtoWriter().apply {
            writeMessage(3, requestId)
            writeMessage(4, clientContext)
        }.toByteArray()

        val imagePayload = ProtoWriter(image.bytes.size + 64).apply {
            writeBytesField(1, image.bytes)
        }.toByteArray()

        val imageMetadata = ProtoWriter().apply {
            writeInt32(1, image.width)
            writeInt32(2, image.height)
        }.toByteArray()

        val imageData = ProtoWriter(image.bytes.size + 128).apply {
            writeMessage(1, imagePayload)
            writeMessage(3, imageMetadata)
        }.toByteArray()

        val objectsRequest = ProtoWriter(image.bytes.size + 256).apply {
            writeMessage(1, requestContext)
            writeMessage(3, imageData)
        }.toByteArray()

        return ProtoWriter(objectsRequest.size + 16).apply {
            writeMessage(1, objectsRequest)
        }.toByteArray()
    }

    /**
     * Decodes a `LensOverlayServerResponse`.
     *
     * @param imageWidth/imageHeight dimensions of the *original* (pre-resize) image, used to turn
     *   the normalised boxes Lens returns back into pixels.
     */
    fun parseResponse(response: ByteArray, imageWidth: Int, imageHeight: Int): ParsedResponse {
        var objectsResponse: ByteArray? = null
        var errorType: Int? = null

        val reader = ProtoReader(response)
        while (true) {
            val tag = reader.nextTag() ?: break
            when (tag.field) {
                1 -> if (tag.wireType == WIRE_LENGTH_DELIMITED) {
                    errorType = parseErrorType(reader.readBytes())
                } else {
                    reader.skip(tag.wireType)
                }

                2 -> if (tag.wireType == WIRE_LENGTH_DELIMITED) {
                    objectsResponse = reader.readBytes()
                } else {
                    reader.skip(tag.wireType)
                }

                else -> reader.skip(tag.wireType)
            }
        }

        val objects = objectsResponse ?: return ParsedResponse(null, emptyList())

        var textMessage: ByteArray? = null
        val objectsReader = ProtoReader(objects)
        while (true) {
            val tag = objectsReader.nextTag() ?: break
            if (tag.field == 3 && tag.wireType == WIRE_LENGTH_DELIMITED) {
                textMessage = objectsReader.readBytes()
            } else {
                objectsReader.skip(tag.wireType)
            }
        }

        val text = textMessage ?: return ParsedResponse(null, emptyList())

        var textLayout: ByteArray? = null
        var contentLanguage: String? = null
        val textReader = ProtoReader(text)
        while (true) {
            val tag = textReader.nextTag() ?: break
            when {
                tag.field == 1 && tag.wireType == WIRE_LENGTH_DELIMITED -> textLayout = textReader.readBytes()
                tag.field == 2 && tag.wireType == WIRE_LENGTH_DELIMITED -> contentLanguage = textReader.readString()
                else -> textReader.skip(tag.wireType)
            }
        }

        // A non-zero error type with an empty payload is what upstream treats as "no text here".
        if (textLayout == null && errorType != null && errorType != SERVER_ERROR_UNKNOWN_TYPE) {
            return ParsedResponse(null, emptyList())
        }
        return ParsedResponse(
            language = contentLanguage,
            paragraphs = parseParagraphs(textLayout ?: return ParsedResponse(contentLanguage, emptyList())),
        )
    }

    private fun parseErrorType(bytes: ByteArray): Int {
        var type = 0
        val reader = ProtoReader(bytes)
        while (true) {
            val tag = reader.nextTag() ?: break
            if (tag.field == 1 && tag.wireType == WIRE_VARINT) type = reader.readVarint().toInt() else reader.skip(tag.wireType)
        }
        return type
    }

    private fun parseParagraphs(layout: ByteArray): List<ParsedParagraph> {
        val paragraphs = ArrayList<ParsedParagraph>()
        val reader = ProtoReader(layout)
        while (true) {
            val tag = reader.nextTag() ?: break
            if (tag.field == 1 && tag.wireType == WIRE_LENGTH_DELIMITED) {
                paragraphs += parseParagraph(reader.readBytes())
            } else {
                reader.skip(tag.wireType)
            }
        }
        return paragraphs
    }

    private fun parseParagraph(bytes: ByteArray): ParsedParagraph {
        val lines = ArrayList<ParsedLine>()
        var geometry: Geometry? = null
        var language: String? = null

        val reader = ProtoReader(bytes)
        while (true) {
            val tag = reader.nextTag() ?: break
            when {
                tag.field == 2 && tag.wireType == WIRE_LENGTH_DELIMITED -> lines += parseLine(reader.readBytes())
                tag.field == 3 && tag.wireType == WIRE_LENGTH_DELIMITED -> geometry = parseGeometry(reader.readBytes())
                tag.field == 5 && tag.wireType == WIRE_LENGTH_DELIMITED -> language = reader.readString()
                else -> reader.skip(tag.wireType)
            }
        }
        return ParsedParagraph(lines, geometry, language)
    }

    private fun parseLine(bytes: ByteArray): ParsedLine {
        val words = ArrayList<ParsedWord>()
        var geometry: Geometry? = null

        val reader = ProtoReader(bytes)
        while (true) {
            val tag = reader.nextTag() ?: break
            when {
                tag.field == 1 && tag.wireType == WIRE_LENGTH_DELIMITED -> words += parseWord(reader.readBytes())
                tag.field == 2 && tag.wireType == WIRE_LENGTH_DELIMITED -> geometry = parseGeometry(reader.readBytes())
                else -> reader.skip(tag.wireType)
            }
        }
        return ParsedLine(words, geometry)
    }

    private fun parseWord(bytes: ByteArray): ParsedWord {
        var plainText = ""
        var separator: String? = null
        var geometry: Geometry? = null

        val reader = ProtoReader(bytes)
        while (true) {
            val tag = reader.nextTag() ?: break
            when {
                tag.field == 2 && tag.wireType == WIRE_LENGTH_DELIMITED -> plainText = reader.readString()
                tag.field == 3 && tag.wireType == WIRE_LENGTH_DELIMITED -> separator = reader.readString()
                tag.field == 4 && tag.wireType == WIRE_LENGTH_DELIMITED -> geometry = parseGeometry(reader.readBytes())
                else -> reader.skip(tag.wireType)
            }
        }
        return ParsedWord(plainText, separator != null, separator ?: "", geometry)
    }

    private fun parseGeometry(bytes: ByteArray): Geometry? {
        val reader = ProtoReader(bytes)
        while (true) {
            val tag = reader.nextTag() ?: break
            if (tag.field == 1 && tag.wireType == WIRE_LENGTH_DELIMITED) {
                return parseBox(reader.readBytes())
            }
            reader.skip(tag.wireType)
        }
        return null
    }

    private fun parseBox(bytes: ByteArray): Geometry {
        var centerX = 0f
        var centerY = 0f
        var width = 0f
        var height = 0f
        var coordinateType = 0

        val reader = ProtoReader(bytes)
        while (true) {
            val tag = reader.nextTag() ?: break
            when {
                tag.field == 1 && tag.wireType == ProtoWriter.WIRE_FIXED32 -> centerX = reader.readFloat()
                tag.field == 2 && tag.wireType == ProtoWriter.WIRE_FIXED32 -> centerY = reader.readFloat()
                tag.field == 3 && tag.wireType == ProtoWriter.WIRE_FIXED32 -> width = reader.readFloat()
                tag.field == 4 && tag.wireType == ProtoWriter.WIRE_FIXED32 -> height = reader.readFloat()
                tag.field == 5 && tag.wireType == ProtoWriter.WIRE_FIXED32 -> reader.readFloat() // rotation
                tag.field == 6 && tag.wireType == WIRE_VARINT -> coordinateType = reader.readVarint().toInt()
                else -> reader.skip(tag.wireType)
            }
        }
        return Geometry(centerX, centerY, width, height, coordinateType)
    }

    // --- decoded shapes ------------------------------------------------------------------------

    class ParsedResponse(val language: String?, val paragraphs: List<ParsedParagraph>)

    class ParsedParagraph(val lines: List<ParsedLine>, val geometry: Geometry?, val language: String?)

    class ParsedLine(val words: List<ParsedWord>, val geometry: Geometry?)

    class ParsedWord(val plainText: String, val hasSeparator: Boolean, val separator: String, val geometry: Geometry?)

    /** Normalised (0..1) box as returned by Lens. */
    data class Geometry(
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float,
        val coordinateType: Int,
    ) {
        val isNormalized: Boolean get() = coordinateType == COORDINATE_TYPE_NORMALIZED

        fun toPixels(imageWidth: Int, imageHeight: Int): PixelBox {
            val w = (width * imageWidth)
            val h = (height * imageHeight)
            val x = (centerX * imageWidth) - (w / 2f)
            val y = (centerY * imageHeight) - (h / 2f)
            return PixelBox(
                x = Math.round(x),
                y = Math.round(y),
                width = Math.round(w),
                height = Math.round(h),
            )
        }
    }
}
