// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.evidence

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.OutcomeReason
import ch.lkmc.chessandcocoa.chess.Piece
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.decide.ConsiderationOutcome
import ch.lkmc.chessandcocoa.coach.decide.DecisionRecord
import ch.lkmc.chessandcocoa.coach.decide.Obligation
import ch.lkmc.chessandcocoa.coach.decide.PlanConsideration
import ch.lkmc.chessandcocoa.coach.decide.SelectionBasis
import ch.lkmc.chessandcocoa.coach.facts.Facts
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus
import ch.lkmc.chessandcocoa.coach.plan.Subgoal

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
        val recorded = decision.selectionBasis == SelectionBasis.PLAN && plan.owner == decision.side &&
            plan.status == PlanStatus.ACTIVE && plan.current.ply == decision.ply &&
            plan.current.action?.move == decision.move && decision.planAssessment?.holds == true
        return recorded && (plan.current.action?.subgoal != Subgoal.STRIKE || strikes(plan, decision.move, before))
    }

    /**
     * The first plan [decision] tested and turned down, for "I looked at a
     * plan to …, but …". Null when a plan drove the move, none was turned
     * down, or the target no longer stands where the record names it (an
     * open plan's target can have moved since its last revision).
     */
    fun turnedDownPlan(decision: DecisionRecord, game: Game): PlanConsideration? {
        if (decision.plan != null) return null
        val before = game.positions.getOrNull(decision.ply) ?: return null
        return decision.considered.firstOrNull { it.outcome in TURNED_DOWN && namesItsTarget(it, decision.side, before) }
    }

    private fun namesItsTarget(consideration: PlanConsideration, side: Side, before: Position): Boolean {
        val piece = before.pieceAt(consideration.target)
        return when (consideration.family) {
            PlanFamily.WIN_WEAK_UNIT -> piece != null && piece.side == side.opposite && piece.type != PieceType.KING
            PlanFamily.INVADE_OPEN_FILE -> piece == Piece(side.opposite, PieceType.PAWN)
            PlanFamily.PROMOTE_PASSED_PAWN -> piece == Piece(side, PieceType.PAWN)
            PlanFamily.ATTACK_KING_SQUARE -> consideration.target in Facts.kingZone(before, side.opposite)
        }
    }

    /** A strike is worded as done (Renderer.intent), so the board must show it: the target taken, the square struck, the pawn promoted. */
    private fun strikes(plan: PlanInstance, move: Move, before: Position): Boolean {
        val target = plan.current.targetSquare
        return when (plan.family) {
            PlanFamily.WIN_WEAK_UNIT, PlanFamily.INVADE_OPEN_FILE ->
                before.isCapture(move) && before.capturedSquare(move) == target &&
                    (plan.target.pieceType == null || before.capturedPiece(move)?.type == plan.target.pieceType)
            PlanFamily.ATTACK_KING_SQUARE -> move.to == target
            PlanFamily.PROMOTE_PASSED_PAWN -> move.from == target && move.promotion != null
        }
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
                val capture = claim.continuation.moves.singleOrNull() ?: return false
                val captures = start.isCapture(capture) && start.capturedSquare(capture) == claim.square && start.capturedPiece(capture)?.type == claim.piece
                captures && start.see(capture) > 0 &&
                    (!claim.alreadyExposed || couldCaptureBefore(game, claim.continuation.fromPly, capture, start)) &&
                    (claim.defense == null || recaptureDefense(claim.defense, claim.continuation, start, capture, game))
            }
            is Claim.MissedWin -> !claim.mate || replay?.last()?.isCheckmate() == true
            is Claim.GameOver -> game.outcome()?.reason == claim.reason || claim.reason == OutcomeReason.RESIGNATION
            else -> true
        }
    }

    /** Both branches must concern the same victim; legal moves exclude pinned, impossible recaptures. */
    private fun recaptureDefense(defense: Continuation, played: Continuation, exposed: Position, capture: Move, game: Game): Boolean {
        if (defense.fromPly != played.fromPly - 1 || defense.moves.size != RECAPTURE_LINE_LENGTH) return false
        val positions = replay(defense, game) ?: return false
        if (defense.moves[1] != capture) return false

        val defended = positions[1]
        val reply = positions[2]
        val recapture = defense.moves.last()
        return defended.capturedSquare(capture) == exposed.capturedSquare(capture) &&
            defended.capturedPiece(capture) == exposed.capturedPiece(capture) && defended.see(capture) <= 0 &&
            reply.capturedSquare(recapture) == capture.to && reply.see(recapture) >= 0
    }

    /** [capture] was already legal, with the turn passed, before the move that led to [ply]. */
    private fun couldCaptureBefore(game: Game, ply: Int, capture: Move, exposed: Position): Boolean {
        val before = game.positions.getOrNull(ply - 1)?.passTurn() ?: return false
        return before.isLegal(capture) && before.isCapture(capture) &&
            before.capturedSquare(capture) == exposed.capturedSquare(capture) &&
            before.capturedPiece(capture) == exposed.capturedPiece(capture) && before.see(capture) > 0
    }

    /** Positions along [continuation], or null when it does not start in [game]'s history or a move is illegal. */
    private fun replay(continuation: Continuation, game: Game): List<Position>? {
        if (continuation.moves.size != continuation.sans.size) return null
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

    private const val RECAPTURE_LINE_LENGTH = 3

    /** Considerations that tested a plan and found it wanting, as opposed to skipping or choosing it. */
    private val TURNED_DOWN = setOf(ConsiderationOutcome.TOO_COSTLY, ConsiderationOutcome.REFUTED, ConsiderationOutcome.NO_ROUTE)
}
