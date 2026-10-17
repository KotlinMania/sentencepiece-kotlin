package io.github.kotlinmania.sentencepiece

/**
 * Error indicating an interior nul byte was found in a C-compatible string.
 */
public class NulError(
    public val nulPosition: Int,
    public val bytes: ByteArray,
) : Exception("nul byte found in provided data at position: $nulPosition") {
    override fun equals(other: Any?): Boolean =
        other is NulError &&
            nulPosition == other.nulPosition &&
            bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * nulPosition + bytes.contentHashCode()
}

/**
 * Errors returned by the `sentencepiece` library.
 */
public enum class CSentencePieceError(
    public val code: Int,
    public val description: String,
) {
    Cancelled(1, "Cancelled"),
    Unknown(2, "Unknown"),
    InvalidArgument(3, "Invalid argument"),
    DeadlineExceeded(4, "Deadline exceeded"),
    NotFound(5, "Not found"),
    AlreadyExists(6, "Already exists"),
    PermissionDenied(7, "Permission denied"),
    ResourceExhausted(8, "Resource exhausted"),
    FailedPrecondition(9, "Failed precondition"),
    Aborted(10, "Aborted"),
    OutOfRange(11, "Out of range"),
    Unimplemented(12, "Unimplemented"),
    Internal(13, "Internal error"),
    Unavailable(14, "Unavailable"),
    DataLoss(15, "Data loss"),
    Unauthenticated(16, "Unauthenticated"),
    ;

    override fun toString(): String = description

    public companion object {
        public fun fromI32(code: Int): CSentencePieceError? =
            entries.firstOrNull { it.code == code }
    }
}

/**
 * Errors produced during SentencePiece model loading, encoding, or decoding.
 */
public sealed class SentencePieceError(
    message: String,
) : Exception(message) {
    public data class CError(
        public val error: CSentencePieceError,
    ) : SentencePieceError("sentencepiece error: $error")

    public class EncodeError : SentencePieceError("sentencepiece could not encode the text") {
        override fun equals(other: Any?): Boolean = other is EncodeError

        override fun hashCode(): Int = 1
    }

    public data class FilenameContainsNul(
        public val path: String,
    ) : SentencePieceError("Filename contains nul: $path")

    public data class FilenameNotUtf8(
        public val path: String,
    ) : SentencePieceError("Filename is not valid UTF-8: $path")

    public data class MissingData(
        public val field: String,
    ) : SentencePieceError("Encoded text did not contain $field")

    public class PieceContainsNul : SentencePieceError("Piece contains nul byte") {
        override fun equals(other: Any?): Boolean = other is PieceContainsNul

        override fun hashCode(): Int = 2
    }

    override fun toString(): String = message.orEmpty()
}
