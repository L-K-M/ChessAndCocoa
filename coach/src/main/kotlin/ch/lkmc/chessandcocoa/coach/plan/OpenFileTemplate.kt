// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.plan

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.facts.Facts

/**
 * Use an open file to reach a pawn, rather than occupying an arbitrary file.
 * Example: Ra1-c1-c7xb7. Geometry proposes the route; the policy and probe
 * judge each step against resistance. Success requires material actually won.
 */
object OpenFileTemplate : PlanTemplate {
    override val family = PlanFamily.INVADE_OPEN_FILE
    override val version = 1

    private const val MAX_TARGETS = 2
    private const val MAX_ACTIONS = 6
    private const val MAX_CHALLENGES = 8
    private const val MIN_TARGET_RANK = 4
    private const val LAST_RANK = 7
    private const val REACHED_PROGRESS = 10

    override fun bind(game: Game, owner: Side): List<PlanTarget> {
        val position = game.current
        if (position.sideToMove != owner || position.squaresOf(owner, PieceType.ROOK).isEmpty()) return emptyList()

        return position.squaresOf(owner.opposite, PieceType.PAWN)
            .filter { (if (owner == Side.WHITE) it.rank else LAST_RANK - it.rank) >= MIN_TARGET_RANK }
            // A pawn already hanging is a tactic, not a new plan.
            .filter { (Facts.bestCaptureSee(position, it, owner) ?: 0) <= 0 }
            .sortedWith(compareBy<Square> { Facts.defenders(position, it).size }.thenBy { it.index })
            .filter { routes(position, owner, it).isNotEmpty() }
            .take(MAX_TARGETS)
            .map { PlanTarget(it, game.pieceIdAt(it), PieceType.PAWN) }
    }

    override fun locate(game: Game, plan: PlanInstance): Square? = plan.target.pieceId?.let { game.squareOf(it) }

    override fun dependencies(game: Game, plan: PlanInstance): List<DependencyState> {
        val target = locate(game, plan)
            ?: return listOf(DependencyState(DependencyKind.TARGET_PRESENT, holds = false))
        val position = game.current
        val rooks = position.squaresOf(plan.owner, PieceType.ROOK)
        return listOf(
            DependencyState(DependencyKind.TARGET_PRESENT, position.pieceAt(target)?.type == PieceType.PAWN, listOf(target)),
            DependencyState(DependencyKind.ATTACKER_PRESENT, rooks.isNotEmpty(), rooks),
            DependencyState(DependencyKind.ROUTE_AVAILABLE, routes(position, plan.owner, target).isNotEmpty(), rooks),
        )
    }

    override fun actions(game: Game, plan: PlanInstance): List<PlanAction> {
        if (game.current.sideToMove != plan.owner) return emptyList()
        val target = locate(game, plan) ?: return emptyList()
        return PlanTemplate.order(game.current, routes(game.current, plan.owner, target), MAX_ACTIONS)
    }

    override fun challenges(game: Game, plan: PlanInstance): List<Move> {
        val position = game.current
        if (position.sideToMove == plan.owner) return emptyList()
        val target = locate(game, plan) ?: return emptyList()
        val before = progress(game, plan)
        return position.legalMoves().filter { move ->
            move.from == target || position.capturedPiece(move)?.type == PieceType.ROOK ||
                progress(game.play(move), plan) < before
        }.sortedBy { it.uci }.take(MAX_CHALLENGES)
    }

    override fun goal(game: Game, plan: PlanInstance): GoalState {
        val target = locate(game, plan)
        if (target != null) return if (game.current.pieceAt(target)?.type == PieceType.PAWN) GoalState.OPEN else GoalState.FAILED

        return if (game.balance(plan.owner) - plan.startBalance >= PieceType.PAWN.value) GoalState.REACHED else GoalState.FAILED
    }

    override fun progress(game: Game, plan: PlanInstance): Int {
        if (goal(game, plan) == GoalState.REACHED) return REACHED_PROGRESS
        val target = locate(game, plan) ?: return 0
        return routes(game.current, plan.owner, target).maxOfOrNull { PlanTemplate.advancement(it.subgoal) } ?: 0
    }

    /** Owner-only geometry is a candidate diagnostic, never a supporting variation. */
    private fun routes(position: Position, owner: Side, target: Square): List<PlanAction> {
        if (position.pieceAt(target)?.type != PieceType.PAWN) return emptyList()
        val asOwner = if (position.sideToMove == owner) position else position.passTurn() ?: return emptyList()
        val pawnFiles = position.pieces().filterValues { it.type == PieceType.PAWN }.keys.map { it.file }.toSet()

        return asOwner.squaresOf(owner, PieceType.ROOK).flatMap { rook ->
            val canAdvance = rook.file !in pawnFiles && canInvade(asOwner, rook, target)
            asOwner.legalMovesFrom(rook).mapNotNull { move ->
                if (move.to == target && rook.file !in pawnFiles && asOwner.see(move) > 0) {
                    return@mapNotNull PlanAction(move, Subgoal.STRIKE)
                }
                if (move.to.file in pawnFiles || asOwner.see(move) < 0) return@mapNotNull null
                val after = asOwner.play(move).passTurn() ?: return@mapNotNull null

                when {
                    move.to.file == rook.file && move.to.rank == target.rank && canWin(after, move.to, target) ->
                        PlanAction(move, Subgoal.ADD_ATTACKER)
                    !canAdvance && move.to.rank == rook.rank && canInvade(after, move.to, target) ->
                        PlanAction(move, Subgoal.OCCUPY_FILE)
                    else -> null
                }
            }
        }
    }

    /** An entry square counts only if the rook can safely reach it and then win the pawn. */
    private fun canInvade(position: Position, rook: Square, target: Square): Boolean {
        val entry = Square.of(rook.file, target.rank)
        if (entry == rook) return false
        val invasion = Move(rook, entry)
        if (!position.isLegal(invasion) || position.see(invasion) < 0) return false
        val after = position.play(invasion).passTurn() ?: return false
        return canWin(after, entry, target)
    }

    private fun canWin(position: Position, rook: Square, target: Square): Boolean {
        val capture = Move(rook, target)
        return position.isLegal(capture) && position.isCapture(capture) && position.see(capture) > 0
    }
}
