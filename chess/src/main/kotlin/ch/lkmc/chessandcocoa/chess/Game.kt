// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

/**
 * An immutable game: an initial position plus the moves played. Keeps the
 * full history because repetition, the engine's view of the game, and piece
 * identity all depend on it (concept Section 11.2).
 */
class Game private constructor(
    val initialFen: String,
    val moves: List<Move>,
    /** positions[i] is the position after i plies; size is moves.size + 1. */
    val positions: List<Position>,
    private val idMaps: List<Map<Square, PieceId>>,
) {
    val current: Position get() = positions.last()
    val ply: Int get() = moves.size

    /** The game after [move]; throws [IllegalArgumentException] when illegal or the game is over. */
    fun play(move: Move): Game {
        require(outcome() == null) { "Game is over" }
        val next = current.play(move)
        return Game(
            initialFen, moves + move, positions + next,
            idMaps + advanceIds(idMaps.last(), current, move),
        )
    }

    /** The game with the last [plies] moves removed. */
    fun undo(plies: Int = 1): Game {
        require(plies in 0..ply) { "Cannot undo $plies plies at ply $ply" }
        return truncate(ply - plies)
    }

    /** The game as it was after [ply] plies. */
    fun truncate(ply: Int): Game {
        require(ply in 0..this.ply) { "Cannot truncate to ply $ply of ${this.ply}" }
        return Game(
            initialFen, moves.subList(0, ply), positions.subList(0, ply + 1), idMaps.subList(0, ply + 1),
        )
    }

    /**
     * The outcome at the current position, or null while the game goes on.
     * Draws by threefold repetition and the fifty-move rule are applied
     * automatically (no claim needed). Checkmate takes precedence.
     */
    fun outcome(): GameOutcome? {
        val pos = current
        if (pos.isCheckmate()) return GameOutcome(pos.sideToMove.opposite, OutcomeReason.CHECKMATE)
        if (pos.isStalemate()) return GameOutcome(null, OutcomeReason.STALEMATE)
        if (pos.hasInsufficientMaterial()) return GameOutcome(null, OutcomeReason.INSUFFICIENT_MATERIAL)
        if (pos.halfmoveClock >= 100) return GameOutcome(null, OutcomeReason.FIFTY_MOVE_RULE)
        val key = pos.repetitionKey()
        if (positions.count { it.repetitionKey() == key } >= 3) {
            return GameOutcome(null, OutcomeReason.THREEFOLD_REPETITION)
        }
        return null
    }

    /** SAN of every move, in order. */
    fun sanMoves(): List<String> = List(moves.size) { positions[it].san(moves[it]) }

    fun uciMoves(): List<String> = moves.map { it.uci }

    /** Identity of every piece on the board after [ply] plies. */
    fun pieceIds(ply: Int = this.ply): Map<Square, PieceId> {
        require(ply in 0..this.ply) { "ply $ply out of range" }
        return idMaps[ply]
    }

    fun pieceIdAt(square: Square, ply: Int = this.ply): PieceId? = pieceIds(ply)[square]

    /** Where the piece [id] stands after [ply] plies; null once captured. */
    fun squareOf(id: PieceId, ply: Int = this.ply): Square? =
        pieceIds(ply).entries.firstOrNull { it.value == id }?.key

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Game) return false
        return initialFen == other.initialFen && moves == other.moves
    }

    override fun hashCode(): Int = 31 * initialFen.hashCode() + moves.hashCode()

    companion object {
        fun new(initialFen: String = Position.START_FEN): Game = build(initialFen, emptyList())

        /** Replays [moves] from [initialFen]; throws when any move is illegal. */
        fun fromMoves(initialFen: String, moves: List<Move>): Game = build(initialFen, moves)

        private fun build(initialFen: String, moves: List<Move>): Game {
            var pos = Position.fromFen(initialFen)
            val positions = ArrayList<Position>(moves.size + 1)
            val idMaps = ArrayList<Map<Square, PieceId>>(moves.size + 1)
            positions += pos
            idMaps += initialIds(pos)
            for (move in moves) {
                idMaps += advanceIds(idMaps.last(), pos, move)
                pos = pos.play(move)
                positions += pos
            }
            return Game(initialFen, moves.toList(), positions, idMaps)
        }

        /** Ids come from the initial position: "w-n-g1", "b-p-e7". */
        private fun initialIds(pos: Position): Map<Square, PieceId> {
            val out = LinkedHashMap<Square, PieceId>(32)
            for ((sq, piece) in pos.pieces()) {
                val prefix = if (piece.side == Side.WHITE) "w" else "b"
                out[sq] = PieceId("$prefix-${piece.type.letter}-${sq.name}")
            }
            return out
        }

        private fun advanceIds(ids: Map<Square, PieceId>, pos: Position, move: Move): Map<Square, PieceId> {
            val next = LinkedHashMap<Square, PieceId>(ids)
            pos.capturedSquare(move)?.let { next.remove(it) }
            val id = next.remove(move.from)
            if (id != null) next[move.to] = id
            if (pos.isCastling(move)) {
                val kingSide = move.to.index > move.from.index
                val rookFrom = Square(if (kingSide) move.to.index + 1 else move.to.index - 2)
                val rookTo = Square(if (kingSide) move.to.index - 1 else move.to.index + 1)
                val rookId = next.remove(rookFrom)
                if (rookId != null) next[rookTo] = rookId
            }
            return next
        }
    }
}
