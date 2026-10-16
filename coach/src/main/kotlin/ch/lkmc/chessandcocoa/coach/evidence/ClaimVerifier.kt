// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.evidence

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.OutcomeReason
import ch.lkmc.chessandcocoa.chess.Position

/**
 * Re-derives claims from their own evidence (concept Section 16.4): every
 * continuation must replay legally from the game state it names, and
 * rule-verified claims must hold on the board. A claim that fails is dropped,
 * never shown (R8).
 */
object ClaimVerifier {

    /** [game] is the live game the claims describe; continuations must start inside its history. */
    fun verified(claims: List<Claim>, game: Game): List<Claim> = claims.filter { verify(it, game) }

    fun verify(claim: Claim, game: Game): Boolean {
        val replay = claim.continuation?.let { replay(it, game) ?: return false }
        return when (claim) {
            is Claim.AllowsMate -> replay?.last()?.isCheckmate() == true && claim.continuation.sans.single() == claim.mateSan
            is Claim.AllowsForcedMate -> replay?.last()?.isCheckmate() == true
            is Claim.ExposesPiece -> {
                val start = replay?.first() ?: return false
                val capture = claim.continuation.moves.first()
                val captures = start.isCapture(capture) && start.capturedSquare(capture) == claim.square && start.capturedPiece(capture)?.type == claim.piece
                captures && (!claim.alreadyExposed || couldCaptureBefore(game, claim.continuation.fromPly, capture))
            }
            is Claim.MissedWin -> !claim.mate || replay?.last()?.isCheckmate() == true
            is Claim.GameOver -> game.outcome()?.reason == claim.reason || claim.reason == OutcomeReason.RESIGNATION
            else -> true
        }
    }

    /** [capture] was already legal, with the turn passed, before the move that led to [ply]. */
    private fun couldCaptureBefore(game: Game, ply: Int, capture: Move): Boolean {
        val before = game.positions.getOrNull(ply - 1)?.passTurn() ?: return false
        return before.isLegal(capture) && before.isCapture(capture)
    }

    /** Positions along [continuation], or null when it does not start in [game]'s history or a move is illegal. */
    private fun replay(continuation: Continuation, game: Game): List<Position>? {
        val start = game.positions.getOrNull(continuation.fromPly) ?: return null
        if (start.fen() != continuation.startFen) return null

        val positions = mutableListOf(start)
        continuation.moves.zip(continuation.sans).forEach { (move, san) ->
            val current = positions.last()
            if (!current.isLegal(move) || current.san(move) != san) return null
            positions += current.play(move)
        }
        return positions
    }
}
