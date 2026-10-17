package io.github.kotlinmania.sentencepiece

/**
 * Byte offset span `[begin, end)` of a sentence piece in a tokenized string.
 */
public data class PieceSpan(
    public val begin: UInt,
    public val end: UInt,
) {
    override fun toString(): String = "($begin, $end)"
}

/**
 * Sentence piece with its identifier and string span.
 */
public data class PieceWithId(
    /** The sentence piece as a string. */
    public val piece: String,
    /** The vocabulary identifier of the sentence piece. */
    public val id: UInt,
    /**
     * The span of the sentence piece in the tokenized string.
     *
     * The span is encoded as the byte offsets `[begin, end)`.
     */
    public val span: PieceSpan,
)

/**
 * Normalized sentence and its byte-offset mapping back to the original input.
 */
public data class NormalizedWithOffsets(
    public val normalized: String,
    public val offsets: List<Int>,
)
