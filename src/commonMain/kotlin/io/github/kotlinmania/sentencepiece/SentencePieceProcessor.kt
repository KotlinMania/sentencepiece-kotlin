package io.github.kotlinmania.sentencepiece

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

internal class VocabPiece(
    val piece: String,
    val score: Float,
    val type: Int,
)

internal class DartsTrie(
    private val units: IntArray,
    private val normalizedBlob: ByteArray,
) {
    private fun hasLeaf(unit: Int): Boolean = ((unit ushr 8) and 1) == 1

    private fun value(unit: Int): Int = unit and 0x7FFFFFFF

    private fun label(unit: Int): Int = unit and (0x80000000.toInt() or 0xFF)

    private fun offset(unit: Int): Int = (unit ushr 10) shl ((unit and (1 shl 9)) ushr 6)

    fun transform(
        input: ByteArray,
        startPos: Int,
    ): Pair<ByteArray, Int>? {
        if (units.isEmpty() || startPos >= input.size) {
            return null
        }
        var nodePos = 0
        var unit = units[nodePos]
        nodePos = nodePos xor offset(unit)
        var bestLen = 0
        var bestVal = -1
        for (i in startPos until input.size) {
            val b = input[i].toInt() and 0xFF
            nodePos = nodePos xor b
            if (nodePos < 0 || nodePos >= units.size) {
                break
            }
            unit = units[nodePos]
            if (label(unit) != b) {
                break
            }
            nodePos = nodePos xor offset(unit)
            if (hasLeaf(unit)) {
                if (nodePos in units.indices) {
                    bestVal = value(units[nodePos])
                    bestLen = i - startPos + 1
                }
            }
        }
        if (bestVal < 0 || bestVal >= normalizedBlob.size) {
            return null
        }
        var end = bestVal
        while (end < normalizedBlob.size && normalizedBlob[end] != 0.toByte()) {
            end += 1
        }
        return Pair(normalizedBlob.copyOfRange(bestVal, end), bestLen)
    }

    companion object {
        fun fromPrecompiledCharsmap(cmap: ByteArray): DartsTrie? {
            if (cmap.size < 4) {
                return null
            }
            val trieSize =
                (cmap[0].toInt() and 0xFF) or
                    ((cmap[1].toInt() and 0xFF) shl 8) or
                    ((cmap[2].toInt() and 0xFF) shl 16) or
                    ((cmap[3].toInt() and 0xFF) shl 24)
            if (trieSize < 0 || trieSize % 4 != 0 || 4 + trieSize > cmap.size) {
                throw SentencePieceError.CError(CSentencePieceError.InvalidArgument)
            }
            val count = trieSize / 4
            val units = IntArray(count)
            for (i in 0 until count) {
                val base = 4 + i * 4
                units[i] =
                    (cmap[base].toInt() and 0xFF) or
                    ((cmap[base + 1].toInt() and 0xFF) shl 8) or
                    ((cmap[base + 2].toInt() and 0xFF) shl 16) or
                    ((cmap[base + 3].toInt() and 0xFF) shl 24)
            }
            val blob = cmap.copyOfRange(4 + trieSize, cmap.size)
            return DartsTrie(units, blob)
        }
    }
}

/**
 * Sentence piece tokenizer.
 *
 * Instances of [SentencePieceProcessor] can be used to tokenize a
 * sentence using a sentencepiece model.
 */
public class SentencePieceProcessor private constructor(
    private val rawModelProto: ByteArray,
    private val vocab: List<VocabPiece>,
    private val pieceToIdMap: Map<String, Int>,
    private val minScore: Float,
    private val unkIdValue: Int,
    private val bosIdValue: Int,
    private val eosIdValue: Int,
    private val padIdValue: Int,
    private val unkSurface: String,
    private val addDummyPrefix: Boolean,
    private val removeExtraWhitespaces: Boolean,
    private val escapeWhitespaces: Boolean,
    private val charsmapTrie: DartsTrie?,
) : AutoCloseable {
    /** Serialize the model to protobuf. */
    public fun toSerializedProto(): ByteArray = rawModelProto.copyOf()

    public fun bosId(): UInt? = if (bosIdValue < 0) null else bosIdValue.toUInt()

    /** Decode a sentence from piece identifiers. */
    public fun decodePieceIds(pieces: UIntArray): String {
        val sb = StringBuilder()
        for (i in pieces.indices) {
            val id = pieces[i].toInt()
            if (id < 0 || id >= vocab.size) {
                throw SentencePieceError.CError(CSentencePieceError.OutOfRange)
            }
            val entry = vocab[id]
            when (entry.type) {
                3 -> Unit
                2 -> sb.append(unkSurface)
                6 -> sb.append(parseBytePiece(entry.piece)?.toInt()?.toChar() ?: entry.piece)
                else -> sb.append(entry.piece)
            }
        }
        return postprocessDecoded(sb.toString())
    }

    public fun decodePieces(pieces: List<String>): String {
        for (piece in pieces) {
            if (piece.indexOf('\u0000') >= 0) {
                throw SentencePieceError.PieceContainsNul()
            }
        }
        val sb = StringBuilder()
        for (piece in pieces) {
            val id = pieceToIdMap[piece]
            if (id != null) {
                val entry = vocab[id]
                when (entry.type) {
                    3 -> Unit
                    2 -> sb.append(unkSurface)
                    else -> sb.append(piece)
                }
            } else {
                sb.append(piece)
            }
        }
        return postprocessDecoded(sb.toString())
    }

    private fun postprocessDecoded(raw: String): String {
        val unescaped = if (escapeWhitespaces) raw.replace("▁", " ") else raw
        return if (addDummyPrefix && unescaped.startsWith(" ")) {
            unescaped.substring(1)
        } else {
            unescaped
        }
    }

    /** Encode a sentence as sentence pieces and their identifiers. */
    public fun encode(sentence: String): List<PieceWithId> {
        val proto = encodeAsSerializedProto(sentence)
        return processEncodeProtobuf(proto)
    }

    internal fun encodeAsSerializedProto(sentence: String): ByteArray {
        if (sentence.isEmpty()) {
            return SentencePieceText(piece = "", pieces = emptyList()).encodeToByteArray()
        }
        val normResult = normalizeWithOffsets(sentence)
        val normBytes = normResult.normalized.encodeToByteArray()
        val offsets = normResult.offsets
        val n = normBytes.size
        val bestScore = FloatArray(n + 1) { -1e38f }
        val bestPrev = IntArray(n + 1) { -1 }
        val bestId = IntArray(n + 1) { unkIdValue }
        val bestIsUnk = BooleanArray(n + 1)
        bestScore[0] = 0.0f
        val unkPenalty = minScore - 10.0f

        var i = 0
        while (i < n) {
            val currentScore = bestScore[i]
            val clen = utf8CharLen(normBytes[i])
            val nextCharPos = minOf(n, i + clen)
            if (currentScore > -1e37f) {
                var hasSingleNode = false
                var j = nextCharPos
                while (j <= n) {
                    val sub = normBytes.decodeToString(i, j)
                    val id = pieceToIdMap[sub]
                    if (id != null) {
                        val entry = vocab[id]
                        if (entry.type == 1 || entry.type == 4) {
                            if (j == nextCharPos) {
                                hasSingleNode = true
                            }
                            val cand = currentScore + entry.score
                            if (cand > bestScore[j]) {
                                bestScore[j] = cand
                                bestPrev[j] = i
                                bestId[j] = id
                                bestIsUnk[j] = false
                            }
                        }
                    }
                    if (j == n) break
                    j += utf8CharLen(normBytes[j])
                }
                if (!hasSingleNode) {
                    val cand = currentScore + unkPenalty
                    if (cand > bestScore[nextCharPos]) {
                        bestScore[nextCharPos] = cand
                        bestPrev[nextCharPos] = i
                        bestId[nextCharPos] = unkIdValue
                        bestIsUnk[nextCharPos] = true
                    }
                }
            }
            i = nextCharPos
        }

        val protoPieces = backtrackPieces(normBytes, offsets, bestPrev, bestId, bestIsUnk)
        return SentencePieceText(
            piece = sentence,
            pieces = protoPieces,
        ).encodeToByteArray()
    }

    private fun backtrackPieces(
        normBytes: ByteArray,
        offsets: List<Int>,
        bestPrev: IntArray,
        bestId: IntArray,
        bestIsUnk: BooleanArray,
    ): List<SentencePiece> {
        val rawSegments = mutableListOf<SentencePiece>()
        var cur = normBytes.size
        while (cur > 0) {
            val prev = bestPrev[cur]
            if (prev < 0) break
            val id = bestId[cur]
            val isUnk = bestIsUnk[cur]
            val surfaceStr = normBytes.decodeToString(prev, cur)
            val pieceStr = if (isUnk) surfaceStr else vocab[id].piece
            rawSegments.add(
                SentencePiece(
                    piece = pieceStr,
                    id = id.toUInt(),
                    surface = surfaceStr,
                    begin = offsets[prev].toUInt(),
                    end = offsets[cur].toUInt(),
                ),
            )
            cur = prev
        }
        rawSegments.reverse()
        return rawSegments
    }

    public fun eosId(): UInt? = if (eosIdValue < 0) null else eosIdValue.toUInt()

    public fun isEmpty(): Boolean = len() == 0

    public fun len(): Int = vocab.size

    /**
     * Normalize a sentence.
     *
     * This applies the normalization rules specified in the model.
     */
    public fun normalize(sentence: String): String = normalizeWithOffsets(sentence).normalized

    /**
     * Normalize a sentence.
     *
     * This applies the normalization rules specified in the model. Returns a
     * [NormalizedWithOffsets] containing the normalized string and a list mapping
     * each byte in the normalized input to the corresponding byte position in the
     * original input.
     */
    public fun normalizeWithOffsets(sentence: String): NormalizedWithOffsets {
        val raw = sentence.encodeToByteArray()
        if (raw.isEmpty()) {
            return NormalizedWithOffsets("", listOf(0))
        }
        val normBytes = ArrayList<Byte>(raw.size + 4)
        val normToOrig = ArrayList<Int>(raw.size + 5)
        val spaceSymbolBytes = "▁".encodeToByteArray()
        var pos = 0
        var prevWasSpace = removeExtraWhitespaces

        while (pos < raw.size) {
            val transformed = charsmapTrie?.transform(raw, pos)
            val replBytes: ByteArray
            val consumed: Int
            if (transformed != null) {
                replBytes = transformed.first
                consumed = transformed.second
            } else {
                val clen = minOf(utf8CharLen(raw[pos]), raw.size - pos)
                replBytes = raw.copyOfRange(pos, pos + clen)
                consumed = clen
            }

            val replStr = replBytes.decodeToString()
            for (ch in replStr) {
                if (ch == ' ') {
                    if (removeExtraWhitespaces && prevWasSpace) {
                        continue
                    }
                    prevWasSpace = true
                } else {
                    if (normBytes.isEmpty() && addDummyPrefix) {
                        for (b in spaceSymbolBytes) {
                            normBytes.add(b)
                            normToOrig.add(pos)
                        }
                    }
                    prevWasSpace = false
                }
                val outBytes =
                    if (ch == ' ' && escapeWhitespaces) {
                        spaceSymbolBytes
                    } else {
                        ch.toString().encodeToByteArray()
                    }
                for (b in outBytes) {
                    normBytes.add(b)
                    normToOrig.add(pos)
                }
            }
            pos += consumed
        }

        normToOrig.add(raw.size)

        if (removeExtraWhitespaces && escapeWhitespaces) {
            val spLen = spaceSymbolBytes.size
            while (normBytes.size >= spLen && endsWithSpaceSymbol(normBytes, spaceSymbolBytes)) {
                repeat(spLen) {
                    normBytes.removeAt(normBytes.size - 1)
                    normToOrig.removeAt(normToOrig.size - 1)
                }
            }
        }

        val finalBytes = ByteArray(normBytes.size) { normBytes[it] }
        return NormalizedWithOffsets(
            normalized = finalBytes.decodeToString(),
            offsets = normToOrig,
        )
    }

    private fun endsWithSpaceSymbol(
        bytes: List<Byte>,
        spaceSymbol: ByteArray,
    ): Boolean {
        val offset = bytes.size - spaceSymbol.size
        for (i in spaceSymbol.indices) {
            if (bytes[offset + i] != spaceSymbol[i]) {
                return false
            }
        }
        return true
    }

    public fun padId(): UInt? = if (padIdValue < 0) null else padIdValue.toUInt()

    /** Get the identifier of a sentence piece. */
    public fun pieceToId(piece: String): UInt? {
        val raw = piece.encodeToByteArray()
        val nulIdx = raw.indexOf(0.toByte())
        if (nulIdx >= 0) {
            throw NulError(nulIdx, raw)
        }
        val id = pieceToIdMap[piece] ?: return null
        if (id == unkIdValue || vocab[id].type == 2) {
            return null
        }
        return id.toUInt()
    }

    /**
     * Encode a sentence using sampling (subword regularization).
     *
     * Sample for the [nBest] segmentations, where [alpha] controls the
     * smoothness of the distribution.
     *
     * When [nBest] is negative (e.g., `-1`), one segmentation is sampled
     * from the hypotheses according to the generation probabilities and using
     * forward-filtering and backward-sampling algorithms.
     *
     * This method throws [IllegalArgumentException] when `nBest > 512` or when
     * [alpha] is not a normal positive floating point number.
     */
    public fun sampleEncode(
        sentence: String,
        nBest: Int,
        alpha: Float,
    ): List<PieceWithId> {
        require(nBest <= 512) { "nBest must be <= 512, got $nBest" }
        val isNormalPositive =
            alpha.isFinite() &&
                alpha > 0.0f &&
                (alpha.toRawBits() and 0x7F800000) != 0
        require(isNormalPositive) { "alpha must be a normal positive float, got $alpha" }

        if (nBest == 0 || nBest == 1) {
            return encode(sentence)
        }
        val proto = sampleEncodeAsSerializedProto(sentence, alpha)
        return processEncodeProtobuf(proto)
    }

    private fun sampleEncodeAsSerializedProto(
        sentence: String,
        alpha: Float,
    ): ByteArray {
        if (sentence.isEmpty()) {
            return SentencePieceText(piece = "", pieces = emptyList()).encodeToByteArray()
        }
        val normResult = normalizeWithOffsets(sentence)
        val normBytes = normResult.normalized.encodeToByteArray()
        val offsets = normResult.offsets
        val n = normBytes.size

        data class LatticeEdge(
            val begin: Int,
            val end: Int,
            val id: Int,
            val score: Float,
            val isUnk: Boolean,
        )

        val edgesEndingAt = Array(n + 1) { mutableListOf<LatticeEdge>() }
        val unkPenalty = minScore - 10.0f
        var i = 0
        while (i < n) {
            val clen = utf8CharLen(normBytes[i])
            val nextCharPos = minOf(n, i + clen)
            var hasSingleNode = false
            var j = nextCharPos
            while (j <= n) {
                val sub = normBytes.decodeToString(i, j)
                val id = pieceToIdMap[sub]
                if (id != null) {
                    val entry = vocab[id]
                    if (entry.type == 1 || entry.type == 4) {
                        if (j == nextCharPos) {
                            hasSingleNode = true
                        }
                        edgesEndingAt[j].add(LatticeEdge(i, j, id, entry.score, false))
                    }
                }
                if (j == n) break
                j += utf8CharLen(normBytes[j])
            }
            if (!hasSingleNode) {
                edgesEndingAt[nextCharPos].add(LatticeEdge(i, nextCharPos, unkIdValue, unkPenalty, true))
            }
            i = nextCharPos
        }

        // Forward filtering (log-sum-exp with temperature alpha)
        val fwd = FloatArray(n + 1) { -1e38f }
        fwd[0] = 0.0f
        for (pos in 1..n) {
            val incoming = edgesEndingAt[pos]
            if (incoming.isEmpty()) continue
            var maxLog = -1e38f
            for (edge in incoming) {
                val v = fwd[edge.begin] + alpha * edge.score
                if (v > maxLog) maxLog = v
            }
            var sumExp = 0.0
            for (edge in incoming) {
                val v = fwd[edge.begin] + alpha * edge.score
                sumExp += exp((v - maxLog).toDouble())
            }
            fwd[pos] = maxLog + ln(max(sumExp, 1e-30)).toFloat()
        }

        // Backward sampling
        val chosen = mutableListOf<SentencePiece>()
        var cur = n
        while (cur > 0) {
            val incoming = edgesEndingAt[cur]
            var maxLog = -1e38f
            for (edge in incoming) {
                val v = fwd[edge.begin] + alpha * edge.score
                if (v > maxLog) maxLog = v
            }
            val weights = DoubleArray(incoming.size)
            var totalWeight = 0.0
            for (idx in incoming.indices) {
                val edge = incoming[idx]
                val w = exp(((fwd[edge.begin] + alpha * edge.score) - maxLog).toDouble())
                weights[idx] = w
                totalWeight += w
            }
            val r = Random.nextDouble() * totalWeight
            var accum = 0.0
            var picked = incoming.last()
            for (idx in incoming.indices) {
                accum += weights[idx]
                if (r <= accum) {
                    picked = incoming[idx]
                    break
                }
            }
            val surfaceStr = normBytes.decodeToString(picked.begin, picked.end)
            val pieceStr = if (picked.isUnk) surfaceStr else vocab[picked.id].piece
            chosen.add(
                SentencePiece(
                    piece = pieceStr,
                    id = picked.id.toUInt(),
                    surface = surfaceStr,
                    begin = offsets[picked.begin].toUInt(),
                    end = offsets[picked.end].toUInt(),
                ),
            )
            cur = picked.begin
        }
        chosen.reverse()
        return SentencePieceText(
            piece = sentence,
            pieces = chosen,
        ).encodeToByteArray()
    }

    public fun unkId(): UInt {
        check(unkIdValue >= 0) { "unkId must always be non-negative" }
        return unkIdValue.toUInt()
    }

    override fun close() {
        // Pure Kotlin model state requires no native deallocation.
    }

    public companion object {
        private val registeredModels: MutableMap<String, ByteArray> = mutableMapOf()

        public fun registerModel(
            path: String,
            data: ByteArray,
        ) {
            registeredModels[path] = data
            val normalizedKey = path.substringAfterLast('/')
            registeredModels[normalizedKey] = data
        }

        internal fun processEncodeProtobuf(proto: ByteArray): List<PieceWithId> {
            if (proto.isEmpty()) {
                throw SentencePieceError.EncodeError()
            }
            val spText = SentencePieceText.decode(proto)
            return spText.pieces.map { protoPiece ->
                val piece = protoPiece.piece ?: throw SentencePieceError.MissingData("piece")
                val id = protoPiece.id ?: throw SentencePieceError.MissingData("id")
                val begin = protoPiece.begin ?: throw SentencePieceError.MissingData("begin")
                val end = protoPiece.end ?: throw SentencePieceError.MissingData("end")
                PieceWithId(
                    piece = piece,
                    id = id,
                    span = PieceSpan(begin, end),
                )
            }
        }

        public fun fromSerializedProto(data: ByteArray): SentencePieceProcessor {
            if (data.isEmpty()) {
                throw SentencePieceError.CError(CSentencePieceError.InvalidArgument)
            }
            val reader =
                try {
                    ProtobufReader(data)
                } catch (_: IllegalArgumentException) {
                    throw SentencePieceError.CError(CSentencePieceError.InvalidArgument)
                }

            val vocab = mutableListOf<VocabPiece>()
            val pieceToIdMap = mutableMapOf<String, Int>()
            var minScore = 0.0f
            var unkId = 0
            var bosId = 1
            var eosId = 2
            var padId = -1
            var unkSurface = " \u2047 "
            var addDummyPrefix = true
            var removeExtraWhitespaces = true
            var escapeWhitespaces = true
            var charsmapTrie: DartsTrie? = null

            try {
                while (reader.hasRemaining()) {
                    val tag = reader.readVarint().toInt()
                    val fieldNumber = tag ushr 3
                    val wireType = tag and 0x07
                    when (fieldNumber) {
                        1 -> {
                            val pieceBytes = reader.readLengthDelimited(wireType)
                            val vp = parseVocabPiece(pieceBytes)
                            val idx = vocab.size
                            vocab.add(vp)
                            if (vp.piece !in pieceToIdMap) {
                                pieceToIdMap[vp.piece] = idx
                            }
                            if ((vp.type == 1 || vp.type == 4) && vp.score < minScore) {
                                minScore = vp.score
                            }
                        }

                        2 -> {
                            val trainerBytes = reader.readLengthDelimited(wireType)
                            val tr = ProtobufReader(trainerBytes)
                            while (tr.hasRemaining()) {
                                val tTag = tr.readVarint().toInt()
                                val tField = tTag ushr 3
                                val tWire = tTag and 0x07
                                when (tField) {
                                    40 -> unkId = tr.readVarintField(tWire).toLong().toInt()
                                    41 -> bosId = tr.readVarintField(tWire).toLong().toInt()
                                    42 -> eosId = tr.readVarintField(tWire).toLong().toInt()
                                    43 -> padId = tr.readVarintField(tWire).toLong().toInt()
                                    44 -> unkSurface = tr.readLengthDelimited(tWire).decodeToString()
                                    else -> tr.skipField(tWire)
                                }
                            }
                        }

                        3 -> {
                            val normBytes = reader.readLengthDelimited(wireType)
                            val nr = ProtobufReader(normBytes)
                            while (nr.hasRemaining()) {
                                val nTag = nr.readVarint().toInt()
                                val nField = nTag ushr 3
                                val nWire = nTag and 0x07
                                when (nField) {
                                    2 -> {
                                        val cmap = nr.readLengthDelimited(nWire)
                                        if (cmap.isNotEmpty()) {
                                            charsmapTrie = DartsTrie.fromPrecompiledCharsmap(cmap)
                                        }
                                    }

                                    3 -> {
                                        addDummyPrefix = nr.readVarintField(nWire) != 0uL
                                    }

                                    4 -> {
                                        removeExtraWhitespaces = nr.readVarintField(nWire) != 0uL
                                    }

                                    5 -> {
                                        escapeWhitespaces = nr.readVarintField(nWire) != 0uL
                                    }

                                    else -> {
                                        nr.skipField(nWire)
                                    }
                                }
                            }
                        }

                        else -> {
                            reader.skipField(wireType)
                        }
                    }
                }
            } catch (e: SentencePieceError) {
                throw e
            } catch (_: IllegalArgumentException) {
                throw SentencePieceError.CError(CSentencePieceError.InvalidArgument)
            }

            if (vocab.isEmpty()) {
                throw SentencePieceError.CError(CSentencePieceError.InvalidArgument)
            }

            return SentencePieceProcessor(
                rawModelProto = data.copyOf(),
                vocab = vocab,
                pieceToIdMap = pieceToIdMap,
                minScore = minScore,
                unkIdValue = unkId,
                bosIdValue = bosId,
                eosIdValue = eosId,
                padIdValue = padId,
                unkSurface = unkSurface,
                addDummyPrefix = addDummyPrefix,
                removeExtraWhitespaces = removeExtraWhitespaces,
                escapeWhitespaces = escapeWhitespaces,
                charsmapTrie = charsmapTrie,
            )
        }

        private fun parseVocabPiece(bytes: ByteArray): VocabPiece {
            val r = ProtobufReader(bytes)
            var piece = ""
            var score = 0.0f
            var type = 1
            while (r.hasRemaining()) {
                val tag = r.readVarint().toInt()
                val fn = tag ushr 3
                val wt = tag and 0x07
                when (fn) {
                    1 -> piece = r.readLengthDelimited(wt).decodeToString()
                    2 -> score = r.readFixed32Float(wt)
                    3 -> type = r.readVarintField(wt).toInt()
                    else -> r.skipField(wt)
                }
            }
            return VocabPiece(piece, score, type)
        }

        /** Open a sentencepiece model from a path. */
        public fun open(path: String): SentencePieceProcessor {
            if (path.indexOf('\u0000') >= 0) {
                throw SentencePieceError.FilenameContainsNul(path)
            }
            val data =
                registeredModels[path]
                    ?: registeredModels[path.substringAfterLast('/')]
                    ?: throw SentencePieceError.CError(CSentencePieceError.NotFound)
            return fromSerializedProto(data)
        }
    }
}

private fun utf8CharLen(firstByte: Byte): Int {
    val b = firstByte.toInt() and 0xFF
    return when {
        (b and 0x80) == 0 -> 1
        (b and 0xE0) == 0xC0 -> 2
        (b and 0xF0) == 0xE0 -> 3
        (b and 0xF8) == 0xF0 -> 4
        else -> 1
    }
}

private fun parseBytePiece(piece: String): Byte? {
    if (piece.length == 6 && piece.startsWith("<0x") && piece.endsWith(">")) {
        val hex = piece.substring(3, 5)
        val v = hex.toIntOrNull(16) ?: return null
        return v.toByte()
    }
    return null
}
