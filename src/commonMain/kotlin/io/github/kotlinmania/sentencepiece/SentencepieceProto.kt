// port-lint: source sentencepiece/src/sentencepiece.rs
package io.github.kotlinmania.sentencepiece

/**
 * Tokenized sentence result encoded as a SentencePiece protobuf message.
 */
public data class SentencePieceText(
    /** User input or postprocessed text. */
    public val piece: String? = null,
    /** A sequence of sentence pieces. */
    public val pieces: List<SentencePiece> = emptyList(),
    /** Score (usually log probability). */
    public val score: Float? = null,
) {
    public fun encodeToByteArray(): ByteArray {
        val out = ProtobufWriter()
        if (piece != null) {
            out.writeBytesField(1, piece.encodeToByteArray())
        }
        for (sp in pieces) {
            out.writeBytesField(2, sp.encodeToByteArray())
        }
        if (score != null) {
            out.writeFloatField(3, score)
        }
        return out.toByteArray()
    }

    public companion object {
        public fun decode(bytes: ByteArray): SentencePieceText {
            val reader = ProtobufReader(bytes)
            var piece: String? = null
            val pieces = mutableListOf<SentencePiece>()
            var score: Float? = null
            while (reader.hasRemaining()) {
                val tag = reader.readVarint().toInt()
                val fieldNumber = tag ushr 3
                val wireType = tag and 0x07
                when (fieldNumber) {
                    1 -> piece = reader.readLengthDelimited(wireType).decodeToString()
                    2 -> pieces.add(SentencePiece.decode(reader.readLengthDelimited(wireType)))
                    3 -> score = reader.readFixed32Float(wireType)
                    else -> reader.skipField(wireType)
                }
            }
            return SentencePieceText(
                piece = piece,
                pieces = pieces,
                score = score,
            )
        }
    }
}

/**
 * Individual sentence piece in a [SentencePieceText] message.
 */
public data class SentencePiece(
    /** Internal representation for the decoder. */
    public val piece: String? = null,
    /** Vocabulary id. */
    public val id: UInt? = null,
    /** External representation for the client. */
    public val surface: String? = null,
    /** Starting position. */
    public val begin: UInt? = null,
    /** End position. */
    public val end: UInt? = null,
) {
    public fun encodeToByteArray(): ByteArray {
        val out = ProtobufWriter()
        if (piece != null) {
            out.writeBytesField(1, piece.encodeToByteArray())
        }
        if (id != null) {
            out.writeVarintField(2, id.toULong())
        }
        if (surface != null) {
            out.writeBytesField(3, surface.encodeToByteArray())
        }
        if (begin != null) {
            out.writeVarintField(4, begin.toULong())
        }
        if (end != null) {
            out.writeVarintField(5, end.toULong())
        }
        return out.toByteArray()
    }

    public companion object {
        public fun decode(bytes: ByteArray): SentencePiece {
            val reader = ProtobufReader(bytes)
            var piece: String? = null
            var id: UInt? = null
            var surface: String? = null
            var begin: UInt? = null
            var end: UInt? = null
            while (reader.hasRemaining()) {
                val tag = reader.readVarint().toInt()
                val fieldNumber = tag ushr 3
                val wireType = tag and 0x07
                when (fieldNumber) {
                    1 -> piece = reader.readLengthDelimited(wireType).decodeToString()
                    2 -> id = reader.readVarintField(wireType).toUInt()
                    3 -> surface = reader.readLengthDelimited(wireType).decodeToString()
                    4 -> begin = reader.readVarintField(wireType).toUInt()
                    5 -> end = reader.readVarintField(wireType).toUInt()
                    else -> reader.skipField(wireType)
                }
            }
            return SentencePiece(
                piece = piece,
                id = id,
                surface = surface,
                begin = begin,
                end = end,
            )
        }
    }
}

internal class ProtobufReader(
    private val data: ByteArray,
) {
    private var pos: Int = 0

    fun hasRemaining(): Boolean = pos < data.size

    fun readVarint(): ULong {
        var result = 0uL
        var shift = 0
        while (true) {
            if (pos >= data.size) {
                throw IllegalArgumentException("Unexpected EOF in protobuf varint")
            }
            val b = data[pos].toInt() and 0xFF
            pos += 1
            result = result or ((b and 0x7F).toULong() shl shift)
            if ((b and 0x80) == 0) {
                return result
            }
            shift += 7
            if (shift >= 64) {
                throw IllegalArgumentException("Protobuf varint overflow")
            }
        }
    }

    fun readVarintField(wireType: Int): ULong {
        require(wireType == 0) { "Expected varint wire type 0, got $wireType" }
        return readVarint()
    }

    fun readLengthDelimited(wireType: Int): ByteArray {
        require(wireType == 2) { "Expected length-delimited wire type 2, got $wireType" }
        val len = readVarint().toInt()
        if (len < 0 || pos + len > data.size) {
            throw IllegalArgumentException("Invalid length-delimited field length: $len")
        }
        val slice = data.copyOfRange(pos, pos + len)
        pos += len
        return slice
    }

    fun readFixed32Float(wireType: Int): Float {
        require(wireType == 5) { "Expected fixed32 wire type 5, got $wireType" }
        if (pos + 4 > data.size) {
            throw IllegalArgumentException("Unexpected EOF in fixed32")
        }
        val b0 = data[pos].toInt() and 0xFF
        val b1 = data[pos + 1].toInt() and 0xFF
        val b2 = data[pos + 2].toInt() and 0xFF
        val b3 = data[pos + 3].toInt() and 0xFF
        pos += 4
        val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        return Float.fromBits(bits)
    }

    fun skipField(wireType: Int) {
        when (wireType) {
            0 -> {
                readVarint()
            }

            1 -> {
                if (pos + 8 > data.size) {
                    throw IllegalArgumentException("Unexpected EOF in fixed64")
                }
                pos += 8
            }

            2 -> {
                val len = readVarint().toInt()
                if (len < 0 || pos + len > data.size) {
                    throw IllegalArgumentException("Unexpected EOF in length-delimited")
                }
                pos += len
            }

            5 -> {
                if (pos + 4 > data.size) {
                    throw IllegalArgumentException("Unexpected EOF in fixed32")
                }
                pos += 4
            }

            else -> {
                throw IllegalArgumentException("Unsupported protobuf wire type: $wireType")
            }
        }
    }
}

internal class ProtobufWriter {
    private var buf: ByteArray = ByteArray(64)
    private var size: Int = 0

    private fun ensureCapacity(extra: Int) {
        if (size + extra > buf.size) {
            var newCap = buf.size * 2
            while (newCap < size + extra) {
                newCap *= 2
            }
            buf = buf.copyOf(newCap)
        }
    }

    private fun writeByte(b: Int) {
        ensureCapacity(1)
        buf[size] = b.toByte()
        size += 1
    }

    private fun writeBytes(bytes: ByteArray) {
        ensureCapacity(bytes.size)
        bytes.copyInto(buf, destinationOffset = size)
        size += bytes.size
    }

    fun writeVarint(value: ULong) {
        var v = value
        while (true) {
            if ((v and 0x7FuL.inv()) == 0uL) {
                writeByte(v.toInt())
                return
            }
            writeByte((v.toInt() and 0x7F) or 0x80)
            v = v shr 7
        }
    }

    fun writeVarintField(
        fieldNumber: Int,
        value: ULong,
    ) {
        writeVarint(((fieldNumber shl 3) or 0).toULong())
        writeVarint(value)
    }

    fun writeBytesField(
        fieldNumber: Int,
        bytes: ByteArray,
    ) {
        writeVarint(((fieldNumber shl 3) or 2).toULong())
        writeVarint(bytes.size.toULong())
        writeBytes(bytes)
    }

    fun writeFloatField(
        fieldNumber: Int,
        value: Float,
    ) {
        writeVarint(((fieldNumber shl 3) or 5).toULong())
        val bits = value.toRawBits()
        writeByte(bits and 0xFF)
        writeByte((bits ushr 8) and 0xFF)
        writeByte((bits ushr 16) and 0xFF)
        writeByte((bits ushr 24) and 0xFF)
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}
