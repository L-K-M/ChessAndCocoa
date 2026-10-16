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
 * "Promote a passed pawn": push an own pawn that no enemy pawn can stop,
 * and support the square in front of it. A later-extension family of
 * concept Section 7.3, added because experiment E3 found the first two too
 * rare in ordinary games. Success is checked outside the template: a
 * promoted piece, with the material kept.
 *
 * ```
 *   . . . . . . . .      the d-pawn is passed: no black pawn on the
 *   . . . . . p p p      c, d or e files in front of it. Pushing it,
 *   . . . P . . . .      or controlling d7 first, are plan steps.
 * ```
 */
object PassedPawnTemplate : PlanTemplate {

    override val family = PlanFamily.PROMOTE_PASSED_PAWN
    override val version = 1

    private const val MAX_ACTIONS = 6
    private const val MAX_CHALLENGES = 8

    /** Relative rank (0 = own back rank) from which a passed pawn is worth a plan. */
    private const val MIN_RELATIVE_RANK = 3

    private const val LAST_RANK = 7
    private const val REACHED_PROGRESS = 10

    override fun bind(game: Game, owner: Side): List<PlanTarget> {
        val position = game.current
        return position.squaresOf(owner, PieceType.PAWN)
            .filter { relativeRank(it, owner) >= MIN_RELATIVE_RANK && isPassed(position, it, owner) }
            .sortedWith(compareByDescending<Square> { relativeRank(it, owner) }.thenBy { it.index })
            .take(1)
            .map { PlanTarget(it, game.pieceIdAt(it), PieceType.PAWN) }
    }

    override fun locate(game: Game, plan: PlanInstance): Square? {
        val id = plan.target.pieceId ?: return null
        return game.squareOf(id)
    }

    override fun dependencies(game: Game, plan: PlanInstance): List<DependencyState> {
        val square = locate(game, plan)
            ?: return listOf(DependencyState(DependencyKind.TARGET_PRESENT, holds = false))
        val position = game.current
        val stillPawn = position.pieceAt(square)?.type == PieceType.PAWN
        return listOf(
            DependencyState(DependencyKind.TARGET_PRESENT, holds = true, pieces = listOf(square)),
            DependencyState(DependencyKind.PAWN_PASSED, holds = !stillPawn || isPassed(position, square, plan.owner), pieces = listOf(square)),
        )
    }

    override fun actions(game: Game, plan: PlanInstance): List<PlanAction> {
        val position = game.current
        if (position.sideToMove != plan.owner) return emptyList()
        val pawn = locate(game, plan) ?: return emptyList()
        if (position.pieceAt(pawn)?.type != PieceType.PAWN) return emptyList()
        val stop = stopSquare(pawn, plan.owner) ?: return emptyList()

        val controlBefore = Facts.effectiveAttackers(position, stop, plan.owner).size
        val actions = position.legalMoves().mapNotNull { move ->
            when {
                move.from == pawn && move.promotion != null ->
                    if (move.promotion == PieceType.QUEEN) PlanAction(move, Subgoal.STRIKE) else null
                move.from == pawn && !position.isCapture(move) -> PlanAction(move, Subgoal.ADVANCE)
                move.to == stop && position.isCapture(move) -> PlanAction(move, Subgoal.REMOVE_DEFENDER)
                move.from != pawn && Facts.effectiveAttackers(position.play(move), stop, plan.owner).size > controlBefore ->
                    PlanAction(move, Subgoal.ADD_ATTACKER)
                else -> null
            }
        }
        return PlanTemplate.order(position, actions, MAX_ACTIONS)
    }

    override fun challenges(game: Game, plan: PlanInstance): List<Move> {
        val position = game.current
        if (position.sideToMove == plan.owner) return emptyList()
        val pawn = locate(game, plan) ?: return emptyList()
        val stop = stopSquare(pawn, plan.owner) ?: return emptyList()
        val enemy = plan.owner.opposite
        val enemyControl = Facts.effectiveAttackers(position, stop, enemy).size

        // Blockade or attack the stop square, or go after the pawn itself.
        return position.legalMoves().filter { move ->
            move.to == stop || move.to == pawn ||
                Facts.effectiveAttackers(position.play(move), stop, enemy).size > enemyControl
        }.sortedBy { it.uci }.take(MAX_CHALLENGES)
    }

    override fun goal(game: Game, plan: PlanInstance): GoalState {
        val square = locate(game, plan) ?: return GoalState.FAILED
        if (game.current.pieceAt(square)?.type == PieceType.PAWN) return GoalState.OPEN

        // Promoted: the goal holds only if the new piece kept material.
        val gained = game.balance(plan.owner) - plan.startBalance
        return if (gained >= PieceType.PAWN.value) GoalState.REACHED else GoalState.FAILED
    }

    override fun progress(game: Game, plan: PlanInstance): Int {
        if (goal(game, plan) == GoalState.REACHED) return REACHED_PROGRESS
        val pawn = locate(game, plan) ?: return 0
        val stop = stopSquare(pawn, plan.owner) ?: return relativeRank(pawn, plan.owner)
        val position = game.current
        val control = Facts.effectiveAttackers(position, stop, plan.owner).size -
            Facts.effectiveAttackers(position, stop, plan.owner.opposite).size
        return relativeRank(pawn, plan.owner) + control
    }

    /** No enemy pawn in front of it on its own or an adjacent file. */
    private fun isPassed(position: Position, pawn: Square, owner: Side): Boolean =
        position.squaresOf(owner.opposite, PieceType.PAWN).none { enemy ->
            kotlin.math.abs(enemy.file - pawn.file) <= 1 && relativeRank(enemy, owner) > relativeRank(pawn, owner)
        }

    private fun relativeRank(square: Square, owner: Side): Int = if (owner == Side.WHITE) square.rank else LAST_RANK - square.rank

    private fun stopSquare(pawn: Square, owner: Side): Square? = pawn.offset(0, if (owner == Side.WHITE) 1 else -1)
}
