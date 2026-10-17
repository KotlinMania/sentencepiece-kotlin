// port-lint: tests sentencepiece/src/lib.rs
package io.github.kotlinmania.sentencepiece

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentencePieceTest {
    private fun toyModelProto(): ByteArray {
        val bytes = TestModels.toyModelBytes
        SentencePieceProcessor.registerModel("testdata/toy.model", bytes)
        return bytes
    }

    private fun toyModel(): SentencePieceProcessor =
        SentencePieceProcessor.fromSerializedProto(toyModelProto())

    private fun albertModel(): SentencePieceProcessor {
        val bytes = TestModels.albertModelBytes
        SentencePieceProcessor.registerModel("testdata/albert.model", bytes)
        return SentencePieceProcessor.open("testdata/albert.model")
    }

    @Test
    fun decodesPieceIdsWithToyModel() {
        val model = toyModel()
        val decoded =
            model.decodePieceIds(
                uintArrayOf(8u, 465u, 10u, 947u, 41u, 10u, 170u, 168u, 110u, 28u, 20u, 143u, 4u),
            )
        assertEquals("I saw a girl with a telescope.", decoded)
    }

    @Test
    fun decodesPiecesWithToyModel() {
        val model = toyModel()
        val pieces =
            listOf(
                "▁I",
                "▁saw",
                "▁a",
                "▁girl",
                "▁with",
                "▁a",
                "▁t",
                "el",
                "es",
                "c",
                "o",
                "pe",
                ".",
            )
        val decoded = model.decodePieces(pieces)
        assertEquals("I saw a girl with a telescope.", decoded)
    }

    @Test
    fun decodeWithIncorrectIdentifierFails() {
        val model = toyModel()
        val err =
            assertFailsWith<SentencePieceError> {
                model.decodePieceIds(uintArrayOf(8u, 1000u))
            }
        assertEquals(SentencePieceError.CError(CSentencePieceError.OutOfRange), err)
    }

    @Test
    fun decodesPiecesWithNullFails() {
        val model = toyModel()
        val pieces = listOf("▁I", "▁s\u0000aw")
        assertFailsWith<SentencePieceError.PieceContainsNul> {
            model.decodePieces(pieces)
        }
    }

    @Test
    fun encodesSentenceWithToyModel() {
        val model = toyModel()
        assertEquals(
            listOf(
                PieceWithId(
                    piece = "▁I",
                    id = 8u,
                    span = PieceSpan(0u, 1u),
                ),
                PieceWithId(
                    piece = "▁saw",
                    id = 465u,
                    span = PieceSpan(1u, 5u),
                ),
                PieceWithId(
                    piece = "▁a",
                    id = 10u,
                    span = PieceSpan(5u, 7u),
                ),
                PieceWithId(
                    piece = "▁girl",
                    id = 947u,
                    span = PieceSpan(7u, 12u),
                ),
                PieceWithId(
                    piece = "▁with",
                    id = 41u,
                    span = PieceSpan(12u, 17u),
                ),
                PieceWithId(
                    piece = "▁a",
                    id = 10u,
                    span = PieceSpan(17u, 19u),
                ),
                PieceWithId(
                    piece = "▁t",
                    id = 170u,
                    span = PieceSpan(19u, 21u),
                ),
                PieceWithId(
                    piece = "el",
                    id = 168u,
                    span = PieceSpan(21u, 23u),
                ),
                PieceWithId(
                    piece = "es",
                    id = 110u,
                    span = PieceSpan(23u, 25u),
                ),
                PieceWithId(
                    piece = "c",
                    id = 28u,
                    span = PieceSpan(25u, 26u),
                ),
                PieceWithId(
                    piece = "o",
                    id = 20u,
                    span = PieceSpan(26u, 27u),
                ),
                PieceWithId(
                    piece = "pe",
                    id = 143u,
                    span = PieceSpan(27u, 29u),
                ),
                PieceWithId(
                    piece = ".",
                    id = 4u,
                    span = PieceSpan(29u, 30u),
                ),
            ),
            model.encode("I saw a girl with a telescope."),
        )
    }

    @Test
    fun sampleEncodesSentenceWithToyModel() {
        val model = toyModel()
        val pieces = model.sampleEncode("I saw a girl with a telescope.", 10, 0.5f)
        // Since sampling is randomized, we cannot check the output,
        // instead check that we can decode the result.
        val pieceIds = UIntArray(pieces.size) { pieces[it].id }
        assertEquals(
            "I saw a girl with a telescope.",
            model.decodePieceIds(pieceIds),
        )
    }

    @Test
    fun sampleEncodeWithIncorrectAlphaFails() {
        val model = toyModel()
        assertFailsWith<IllegalArgumentException> {
            model.sampleEncode("I saw a girl with a telescope.", 10, 0.0f)
        }
    }

    @Test
    fun sampleEncodeWithIncorrectNBestFails() {
        val model = toyModel()
        assertFailsWith<IllegalArgumentException> {
            model.sampleEncode("I saw a girl with a telescope.", 513, 0.1f)
        }
    }

    @Test
    fun sampleEncodeWithNegativeNBest() {
        val model = toyModel()
        val pieces = model.sampleEncode("I saw a girl with a telescope.", -1, 0.5f)
        // Since sampling is randomized, we cannot check the output,
        // instead check that we can decode the result.
        val pieceIds = UIntArray(pieces.size) { pieces[it].id }
        assertEquals(
            "I saw a girl with a telescope.",
            model.decodePieceIds(pieceIds),
        )
    }

    @Test
    fun errorsOnPathWithNul() {
        val testPath = "test\u0000path"
        val err =
            assertFailsWith<SentencePieceError> {
                SentencePieceProcessor.open(testPath)
            }
        assertEquals(SentencePieceError.FilenameContainsNul(testPath), err)
    }

    @Test
    fun failsLoadingNonexistingModel() {
        val err =
            assertFailsWith<SentencePieceError> {
                SentencePieceProcessor.open("non-existing")
            }
        assertEquals(SentencePieceError.CError(CSentencePieceError.NotFound), err)
    }

    @Test
    fun handlesNulCharacter() {
        val model = toyModel()
        assertEquals(
            listOf(
                PieceWithId(
                    piece = "▁T",
                    id = 239u,
                    span = PieceSpan(0u, 1u),
                ),
                PieceWithId(
                    piece = "est",
                    id = 382u,
                    span = PieceSpan(1u, 4u),
                ),
                PieceWithId(
                    piece = "\u0000",
                    id = 0u,
                    span = PieceSpan(4u, 5u),
                ),
                PieceWithId(
                    piece = "▁",
                    id = 7u,
                    span = PieceSpan(5u, 6u),
                ),
                PieceWithId(
                    piece = "n",
                    id = 24u,
                    span = PieceSpan(6u, 7u),
                ),
                PieceWithId(
                    piece = "ul",
                    id = 231u,
                    span = PieceSpan(7u, 9u),
                ),
            ),
            model.encode("Test\u0000 nul"),
        )
    }

    @Test
    fun loadsModelFromSerializedProtobuf() {
        val model = toyModel()
        assertTrue(!model.isEmpty())
    }

    @Test
    fun canLookupPieceId() {
        val model = toyModel()
        assertEquals(143u, model.pieceToId("pe"))
        assertNull(model.pieceToId("unknown"))
    }

    @Test
    fun canLookupBosId() {
        val model = toyModel()
        assertEquals(1u, model.bosId())
    }

    @Test
    fun canLookupEosId() {
        val model = toyModel()
        assertEquals(2u, model.eosId())
    }

    @Test
    fun canLookupPadId() {
        val model = toyModel()
        // Fixme: the toy model was trained without a padding index.
        assertNull(model.padId())
    }

    @Test
    fun canLookupUnkId() {
        val model = toyModel()
        assertEquals(0u, model.unkId())
    }

    @Test
    fun modelHasCorrectLen() {
        val model = toyModel()
        assertEquals(1000, model.len())
    }

    @Test
    fun protobufRoundtripIsIdentical() {
        val protobuf = toyModelProto()
        val spp = SentencePieceProcessor.fromSerializedProto(protobuf)
        val protobufRoundtrip = spp.toSerializedProto()
        assertContentEquals(protobuf, protobufRoundtrip)
    }

    @Test
    fun normalizesSentenceWithToyModel() {
        val model = toyModel()
        assertEquals(
            "▁KADOKAWA▁ABC",
            model.normalize("ＫＡＤＯＫＡＷＡ   ABC "),
        )
    }

    @Test
    fun testNormalizeWithOffsets() {
        toyModelProto()
        val spp = SentencePieceProcessor.open("testdata/toy.model")

        val (normalized, offsets) = spp.normalizeWithOffsets("ＫＡＤＯＫＡＷＡ   ABC ")
        assertEquals("▁KADOKAWA▁ABC", normalized)
        assertEquals(
            listOf(0, 0, 0, 0, 3, 6, 9, 12, 15, 18, 21, 24, 24, 24, 27, 28, 29, 30),
            offsets,
        )
    }

    @Test
    fun canLookupBosIdAlbert() {
        val model = albertModel()
        assertNull(model.bosId())
    }

    @Test
    fun canLookupEosIdAlbert() {
        val model = albertModel()
        assertNull(model.eosId())
    }

    @Test
    fun canLookupUnkIdAlbert() {
        val model = albertModel()
        assertEquals(1u, model.unkId())
    }

    @Test
    fun decodesSentenceWithAlbertModel() {
        val model = albertModel()
        val decoded =
            model.decodePieceIds(
                uintArrayOf(
                    13u,
                    1u,
                    1514u,
                    102u,
                    1276u,
                    3066u,
                    20u,
                    25277u,
                    21000u,
                    16689u,
                    18u,
                    26u,
                    3784u,
                    9u,
                ),
            )
        assertEquals(
            " ⁇ ardly anyone attempted to decipher hieroglyphs for decades.",
            decoded,
        )
    }

    @Test
    fun encodesSentenceWithAlbertModel() {
        val model = albertModel()
        assertEquals(
            listOf(
                PieceWithId(
                    piece = "▁",
                    id = 13u,
                    span = PieceSpan(0u, 0u),
                ),
                PieceWithId(
                    piece = "H",
                    id = 1u,
                    span = PieceSpan(0u, 1u),
                ),
                PieceWithId(
                    piece = "ard",
                    id = 1514u,
                    span = PieceSpan(1u, 4u),
                ),
                PieceWithId(
                    piece = "ly",
                    id = 102u,
                    span = PieceSpan(4u, 6u),
                ),
                PieceWithId(
                    piece = "▁anyone",
                    id = 1276u,
                    span = PieceSpan(6u, 13u),
                ),
                PieceWithId(
                    piece = "▁attempted",
                    id = 3066u,
                    span = PieceSpan(13u, 23u),
                ),
                PieceWithId(
                    piece = "▁to",
                    id = 20u,
                    span = PieceSpan(23u, 26u),
                ),
                PieceWithId(
                    piece = "▁decipher",
                    id = 25277u,
                    span = PieceSpan(26u, 35u),
                ),
                PieceWithId(
                    piece = "▁hiero",
                    id = 21000u,
                    span = PieceSpan(35u, 41u),
                ),
                PieceWithId(
                    piece = "glyph",
                    id = 16689u,
                    span = PieceSpan(41u, 46u),
                ),
                PieceWithId(
                    piece = "s",
                    id = 18u,
                    span = PieceSpan(46u, 47u),
                ),
                PieceWithId(
                    piece = "▁for",
                    id = 26u,
                    span = PieceSpan(47u, 51u),
                ),
                PieceWithId(
                    piece = "▁decades",
                    id = 3784u,
                    span = PieceSpan(51u, 59u),
                ),
                PieceWithId(
                    piece = ".",
                    id = 9u,
                    span = PieceSpan(59u, 60u),
                ),
            ),
            model.encode("Hardly anyone attempted to decipher hieroglyphs for decades."),
        )
    }

    @Test
    fun loadsModelAlbert() {
        val model = albertModel()
        assertTrue(!model.isEmpty())
    }
}
