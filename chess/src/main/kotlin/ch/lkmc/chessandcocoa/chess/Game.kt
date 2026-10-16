// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

/**
 * An immutable game: an initial position plus the moves played. Keeps the
 * full history because repetition, the engine's view of the game, and piece
 * identity all depend on it (concept Section 11.2).
 */
class Game private constructor() {

    val initialFen: String get() = TODO()
    val moves: List<Move> get() = TODO()

    /** positions[i] is the position after i plies; size is moves.size + 1. */
    val positions: List<Position> get() = TODO()

    val current: Position get() = TODO()
    val ply: Int get() = TODO()

    /** The game after [move]; throws [IllegalArgumentException] when illegal or the game is over. */
    fun play(move: Move): Game = TODO()

    /** The game with the last [plies] moves removed. */
    fun undo(plies: Int = 1): Game = TODO()

    /** The game as it was after [ply] plies. */
    fun truncate(ply: Int): Game = TODO()

    /**
     * The outcome at the current position, or null while the game goes on.
     * Draws by threefold repetition and the fifty-move rule are applied
     * automatically (no claim needed). Checkmate takes precedence.
     */
    fun outcome(): GameOutcome? = TODO()

    /** SAN of every move, in order. */
    fun sanMoves(): List<String> = TODO()

    fun uciMoves(): List<String> = TODO()

    /** Identity of every piece on the board after [ply] plies. */
    fun pieceIds(ply: Int = this.ply): Map<Square, PieceId> = TODO()

    fun pieceIdAt(square: Square, ply: Int = this.ply): PieceId? = TODO()

    /** Where the piece [id] stands after [ply] plies; null once captured. */
    fun squareOf(id: PieceId, ply: Int = this.ply): Square? = TODO()

    override fun equals(other: Any?): Boolean = TODO()

    override fun hashCode(): Int = TODO()

    companion object {
        fun new(initialFen: String = Position.START_FEN): Game = TODO()

        /** Replays [moves] from [initialFen]; throws when any move is illegal. */
        fun fromMoves(initialFen: String, moves: List<Move>): Game = TODO()
    }
}
