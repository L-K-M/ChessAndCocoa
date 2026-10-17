// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.evidence

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.OutcomeReason
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.coach.decide.DecisionRecord
import ch.lkmc.chessandcocoa.coach.decide.Obligation
import ch.lkmc.chessandcocoa.coach.decide.SelectionBasis
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus

/**
 * Re-derives claims from their own evidence (concept Section 16.4): every
 * continuation must replay legally from the game state it names, and
 * rule-verified claims must hold on the board. A claim that fails is dropped,
 * never shown (R8).
 */
object ClaimVerifier {

    /** Intent wording must describe the decision that actually produced this position. */
    fun verifyIntent(decision: DecisionRecord, game: Game): Boolean {
        val before = game.positions.getOrNull(decision.ply) ?: return false
        if (game.ply != decision.ply + 1 || game.moves.lastOrNull() != decision.move) return false
        if (before.fen() != decision.fen || before.sideToMove != decision.side) return false
        if (decision.history != game.uciMoves().take(decision.ply)) return false
        if (!before.isLegal(decision.move) || before.san(decision.move) != decision.san) return false
        if (Obligation.FORCED_MOVE in decision.obligations && before.legalMoves().size != 1) return false
        if (Obligation.IN_CHECK in decision.obligations && !before.isCheck()) return false

        val plan = decision.plan ?: return decision.selectionBasis != SelectionBasis.PLAN
        return decision.selectionBasis == SelectionBasis.PLAN && plan.owner == decision.side &&
            plan.status == PlanStatus.ACTIVE && plan.current.ply == decision.ply &&
            plan.current.action?.move == decision.move && decision.planAssessment?.holds == true
    }

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
