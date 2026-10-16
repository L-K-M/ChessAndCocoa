// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
enum class Side {
    WHITE,
    BLACK;

    val opposite: Side get() = if (this == WHITE) BLACK else WHITE
}

/**
 * Piece kinds with the beginner material scale (knight = bishop = 3 pawns).
 * The value feeds static exchange estimates and material counts, never
 * engine-quality judgments.
 */
@Serializable
enum class PieceType(val letter: Char, val value: Int) {
    PAWN('p', 100),
    KNIGHT('n', 300),
    BISHOP('b', 300),
    ROOK('r', 500),
    QUEEN('q', 900),
    KING('k', 0);

    companion object {
        fun fromLetter(letter: Char): PieceType =
            entries.firstOrNull { it.letter == letter.lowercaseChar() }
                ?: throw IllegalArgumentException("Unknown piece letter: $letter")
    }
}

@Serializable
data class Piece(val side: Side, val type: PieceType) {
    /** FEN letter: uppercase for White, lowercase for Black. */
    val fenChar: Char get() = if (side == Side.WHITE) type.letter.uppercaseChar() else type.letter
}

/** A board square, a1 = 0 … h8 = 63. Serialized as its name, e.g. "e4". */
@Serializable(with = SquareSerializer::class)
@JvmInline
value class Square(val index: Int) {
    init {
        require(index in 0..63) { "Square index out of range: $index" }
    }

    val file: Int get() = index % 8
    val rank: Int get() = index / 8
    val name: String get() = "${'a' + file}${'1' + rank}"

    /** The square shifted by (df, dr), or null when it would leave the board. */
    fun offset(df: Int, dr: Int): Square? {
        val f = file + df
        val r = rank + dr
        return if (f in 0..7 && r in 0..7) of(f, r) else null
    }

    override fun toString(): String = name

    companion object {
        val all: List<Square> = (0..63).map { Square(it) }

        fun of(file: Int, rank: Int): Square = Square(rank * 8 + file)

        fun parse(name: String): Square {
            require(name.length == 2 && name[0] in 'a'..'h' && name[1] in '1'..'8') { "Bad square: $name" }
            return of(name[0] - 'a', name[1] - '1')
        }
    }
}

object SquareSerializer : KSerializer<Square> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Square", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Square) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): Square = Square.parse(decoder.decodeString())
}

/** A move in machine form. Castling is the king's two-square move (e1g1). */
@Serializable
data class Move(val from: Square, val to: Square, val promotion: PieceType? = null) {
    val uci: String get() = from.name + to.name + (promotion?.letter ?: "")

    override fun toString(): String = uci

    companion object {
        fun parseUci(uci: String): Move {
            require(uci.length == 4 || uci.length == 5) { "Bad UCI move: $uci" }
            val promotion = if (uci.length == 5) PieceType.fromLetter(uci[4]) else null
            return Move(Square.parse(uci.substring(0, 2)), Square.parse(uci.substring(2, 4)), promotion)
        }
    }
}

@Serializable
data class CastlingRights(
    val whiteKingSide: Boolean,
    val whiteQueenSide: Boolean,
    val blackKingSide: Boolean,
    val blackQueenSide: Boolean,
) {
    companion object {
        val ALL = CastlingRights(true, true, true, true)
        val NONE = CastlingRights(false, false, false, false)
    }
}

/**
 * Stable identity of a piece across a game (concept Section 11.2): a square
 * alone does not survive movement, capture or promotion. Assigned from the
 * piece's square in the game's initial position, e.g. "w-n-g1"; a promoted
 * pawn keeps its pawn's id.
 */
@Serializable
@JvmInline
value class PieceId(val value: String) {
    override fun toString(): String = value
}

@Serializable
enum class OutcomeReason {
    CHECKMATE,
    STALEMATE,
    INSUFFICIENT_MATERIAL,
    FIFTY_MOVE_RULE,
    THREEFOLD_REPETITION,
    RESIGNATION,
}

/** A finished game: [winner] is null for a draw. */
@Serializable
data class GameOutcome(val winner: Side?, val reason: OutcomeReason)
