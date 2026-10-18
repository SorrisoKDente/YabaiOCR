package dev.yabai.ocr.core.proto

/**
 * Minimal protobuf (proto3) wire-format writer.
 *
 * Only the handful of primitives used by the Google Lens overlay protocol are implemented.
 * Keeping this in-house means the module has zero codegen steps and works identically on the
 * JVM (unit tests) and on Android (no javac plugin, no protoc download).
 */
class ProtoWriter(initialCapacity: Int = 128) {

    private var buf = ByteArray(initialCapacity)
    private var len = 0

    private fun ensure(extra: Int) {
        if (len + extra <= buf.size) return
        var cap = buf.size
        while (cap < len + extra) cap = cap shl 1
        buf = buf.copyOf(cap)
    }

    private fun writeRaw(b: Int) {
        ensure(1)
        buf[len++] = b.toByte()
    }

    private fun writeRaw(bytes: ByteArray) {
        ensure(bytes.size)
        System.arraycopy(bytes, 0, buf, len, bytes.size)
        len += bytes.size
    }

    fun toByteArray(): ByteArray = buf.copyOf(len)

    val size: Int get() = len

    /** Base-128 varint, valid for the whole 64-bit range (uses unsigned shift). */
    fun writeVarint(value: Long) {
        var v = value
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v == 0L) {
                writeRaw(b)
                return
            }
            writeRaw(b or 0x80)
        }
    }

    fun writeTag(field: Int, wireType: Int) {
        writeVarint(((field.toLong() shl 3) or wireType.toLong()))
    }

    fun writeInt32(field: Int, value: Int) {
        writeTag(field, WIRE_VARINT)
        // proto3 int32 is sign-extended to 64 bits.
        writeVarint(value.toLong())
    }

    fun writeUInt64(field: Int, value: Long) {
        writeTag(field, WIRE_VARINT)
        writeVarint(value)
    }

    fun writeEnum(field: Int, value: Int) = writeInt32(field, value)

    fun writeBool(field: Int, value: Boolean) {
        writeTag(field, WIRE_VARINT)
        writeVarint(if (value) 1L else 0L)
    }

    fun writeFloat(field: Int, value: Float) {
        writeTag(field, WIRE_FIXED32)
        val bits = value.toRawBits()
        ensure(4)
        buf[len++] = (bits and 0xFF).toByte()
        buf[len++] = ((bits ushr 8) and 0xFF).toByte()
        buf[len++] = ((bits ushr 16) and 0xFF).toByte()
        buf[len++] = ((bits ushr 24) and 0xFF).toByte()
    }

    fun writeBytesField(field: Int, value: ByteArray) {
        writeTag(field, WIRE_LENGTH_DELIMITED)
        writeVarint(value.size.toLong())
        writeRaw(value)
    }

    fun writeString(field: Int, value: String) = writeBytesField(field, value.toByteArray(Charsets.UTF_8))

    fun writeMessage(field: Int, value: ByteArray) = writeBytesField(field, value)

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH_DELIMITED = 2
        const val WIRE_FIXED32 = 5
    }
}

/** Minimal protobuf wire-format reader over a byte range. */
class ProtoReader(private val buf: ByteArray, private var pos: Int, private val end: Int) {

    constructor(buf: ByteArray) : this(buf, 0, buf.size)

    data class Tag(val field: Int, val wireType: Int)

    fun hasMore(): Boolean = pos < end

    /** Returns the next tag, or null when the message is exhausted. */
    fun nextTag(): Tag? {
        if (pos >= end) return null
        val key = readVarint()
        return Tag((key ushr 3).toInt(), (key and 0x07).toInt())
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= end) throw ProtoException("unexpected end of buffer while reading varint")
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 63) return result
        }
    }

    fun readBytes(): ByteArray {
        val n = readVarint().toInt()
        if (n < 0 || pos + n > end) throw ProtoException("length-delimited field overruns buffer ($n)")
        val out = buf.copyOfRange(pos, pos + n)
        pos += n
        return out
    }

    fun readString(): String = String(readBytes(), Charsets.UTF_8)

    fun readFloat(): Float = Float.fromBits(readFixed32())

    fun readDouble(): Double = java.lang.Double.longBitsToDouble(readFixed64())

    private fun readFixed32(): Int {
        if (pos + 4 > end) throw ProtoException("unexpected end of buffer while reading fixed32")
        val v = (buf[pos].toInt() and 0xFF) or
            ((buf[pos + 1].toInt() and 0xFF) shl 8) or
            ((buf[pos + 2].toInt() and 0xFF) shl 16) or
            ((buf[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4
        return v
    }

    private fun readFixed64(): Long {
        if (pos + 8 > end) throw ProtoException("unexpected end of buffer while reading fixed64")
        var v = 0L
        for (i in 0 until 8) v = v or ((buf[pos + i].toLong() and 0xFF) shl (8 * i))
        pos += 8
        return v
    }

    fun skip(wireType: Int) {
        when (wireType) {
            ProtoWriter.WIRE_VARINT -> readVarint()
            ProtoWriter.WIRE_FIXED64 -> pos += 8
            ProtoWriter.WIRE_LENGTH_DELIMITED -> {
                // Read the length *before* touching pos: `pos += readVarint()` would evaluate the
                // left-hand side first and silently land one byte short.
                val length = readVarint().toInt()
                pos += length
            }

            ProtoWriter.WIRE_FIXED32 -> pos += 4
            else -> throw ProtoException("unsupported wire type $wireType")
        }
        if (pos > end) throw ProtoException("skipped past end of buffer")
    }

    /** Current cursor offset, exposed for tests and diagnostics. */
    val position: Int get() = pos
}

class ProtoException(message: String) : RuntimeException(message)
