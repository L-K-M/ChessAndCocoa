// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.plan

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.OutcomeReason
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.facts.Facts

/**
 * "Attack a weak king square" (concept Section 7.3): a square in front of
 * the enemy king that only the king defends, such as f7 before Black has
 * castled. The plan brings attackers to it until a strike there mates or
 * wins material.
 *
 * ```
 *   8  r . b q k b n r      f7 is guarded by the king alone;
 *   7  p p p p . p p p      Bc4 already aims at it, Qh5 or Qf3
 *   6  . . n . . . . .      would add a second attacker and
 *   5  . . . . p . . .      threaten Qxf7#.
 *   4  . . B . P . . .
 * ```
 */
object AttackKingSquareTemplate : PlanTemplate {

    override val family = PlanFamily.ATTACK_KING_SQUARE
    override val version = 1

    private const val MAX_ACTIONS = 6
    private const val MAX_CHALLENGES = 8

    /** Progress score once the strike has mated or won material. */
    private const val REACHED_PROGRESS = 10

    override fun bind(game: Game, owner: Side): List<PlanTarget> {
        val position = game.current
        val enemy = owner.opposite

        // A mate available now is a tactic, not a plan.
        if (Facts.mateInOneMoves(position).isNotEmpty()) return emptyList()

        return frontSquares(position, enemy)
            .filter { square -> onlyKingDefends(position, square, enemy) }
            .filter { square -> (Facts.bestCaptureSee(position, square, owner) ?: 0) <= 0 }
            .map { square -> square to Facts.effectiveAttackers(position, square, owner).size }
            .filter { (_, attackers) -> attackers >= 1 }
            .sortedWith(compareByDescending<Pair<Square, Int>> { it.second }.thenBy { it.first.index })
            .take(1)
            .map { (square, _) -> PlanTarget(square, pieceType = position.pieceAt(square)?.type) }
    }

    override fun locate(game: Game, plan: PlanInstance): Square = plan.target.square

    override fun dependencies(game: Game, plan: PlanInstance): List<DependencyState> {
        val position = game.current
        val square = plan.target.square
        val enemy = plan.owner.opposite

        val kingNear = square in Facts.kingZone(position, enemy)
        val attackers = Facts.effectiveAttackers(position, square, plan.owner)
        val guards = nonKingDefenders(position, square, enemy)

        return listOf(
            DependencyState(DependencyKind.KING_NEAR_SQUARE, holds = kingNear, pieces = listOf(position.kingSquare(enemy))),
            DependencyState(DependencyKind.SQUARE_WEAK, holds = guards.size < attackers.size || guards.isEmpty(), pieces = guards),
            DependencyState(DependencyKind.ATTACKER_PRESENT, holds = attackers.isNotEmpty(), pieces = attackers),
        )
    }

    override fun actions(game: Game, plan: PlanInstance): List<PlanAction> {
        val position = game.current
        if (position.sideToMove != plan.owner) return emptyList()

        val square = plan.target.square
        val attackersBefore = Facts.effectiveAttackers(position, square, plan.owner).size

        val actions = position.legalMoves().mapNotNull { move ->
            val after = position.play(move)
            if (move.to == square) {
                val strikes = after.isCheckmate() || (position.isCapture(move) && position.see(move) >= 0)
                return@mapNotNull if (strikes) PlanAction(move, Subgoal.STRIKE) else null
            }

            val attackersAfter = Facts.effectiveAttackers(after, square, plan.owner).size
            if (attackersAfter <= attackersBefore) return@mapNotNull null

            // Diagnostic only: would the owner have a mate on the square if it moved again?
            val threat = Facts.threatenedMates(after, plan.owner).any { it.to == square }
            PlanAction(move, if (threat) Subgoal.CREATE_THREAT else Subgoal.ADD_ATTACKER)
        }

        return PlanTemplate.order(position, actions, MAX_ACTIONS)
    }

    override fun challenges(game: Game, plan: PlanInstance): List<Move> {
        val position = game.current
        if (position.sideToMove == plan.owner) return emptyList()

        val square = plan.target.square
        val enemy = plan.owner.opposite
        val attackers = Facts.effectiveAttackers(position, square, plan.owner)
        val guardsBefore = nonKingDefenders(position, square, enemy).size
        val king = position.kingSquare(enemy)

        return position.legalMoves().filter { move ->
            if (move.from == king) return@filter true
            if (move.to in attackers) return@filter true
            val after = position.play(move)
            nonKingDefenders(after, square, enemy).size > guardsBefore ||
                Facts.effectiveAttackers(after, square, plan.owner).size < attackers.size ||
                attackers.any { it in after.attackedSquares(move.to) }
        }.sortedBy { it.uci }.take(MAX_CHALLENGES)
    }

    override fun progress(game: Game, plan: PlanInstance): Int {
        if (goal(game, plan) == GoalState.REACHED) return REACHED_PROGRESS
        val position = game.current
        val square = plan.target.square
        val attackers = Facts.effectiveAttackers(position, square, plan.owner).size
        val guards = nonKingDefenders(position, square, plan.owner.opposite).size
        val threat = if (Facts.threatenedMates(position, plan.owner).any { it.to == square }) 1 else 0
        return attackers - guards + threat
    }

    override fun goal(game: Game, plan: PlanInstance): GoalState {
        val outcome = game.outcome()
        if (outcome?.reason == OutcomeReason.CHECKMATE && outcome.winner == plan.owner) return GoalState.REACHED

        val struck = plan.revisions.lastOrNull { it.action != null }?.action?.subgoal == Subgoal.STRIKE
        val gained = game.balance(plan.owner) - plan.startBalance
        if (struck && gained >= PieceType.PAWN.value) return GoalState.REACHED

        val kingNear = plan.target.square in Facts.kingZone(game.current, plan.owner.opposite)
        return if (kingNear) GoalState.OPEN else GoalState.FAILED
    }

    /** Squares diagonally or straight in front of [side]'s king, toward the opponent. */
    private fun frontSquares(position: Position, side: Side): List<Square> {
        val king = position.kingSquare(side)
        val forward = if (side == Side.WHITE) 1 else -1
        return (-1..1).mapNotNull { df -> king.offset(df, forward) }
            .filter { square ->
                val piece = position.pieceAt(square)
                piece == null || (piece.side == side && piece.type == PieceType.PAWN)
            }
    }

    private fun onlyKingDefends(position: Position, square: Square, side: Side): Boolean {
        val king = position.kingSquare(side)
        val defenders = Facts.effectiveAttackers(position, square, side)
        return defenders == listOf(king)
    }

    private fun nonKingDefenders(position: Position, square: Square, side: Side): List<Square> {
        val king = position.kingSquare(side)
        return Facts.effectiveAttackers(position, square, side).filter { it != king }
    }
}
