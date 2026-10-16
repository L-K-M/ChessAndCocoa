// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.plan

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.facts.Facts

/**
 * "Win a weak unit" (concept Section 7.3): add attackers to, or remove
 * defenders of, an under-defended or pinned enemy piece or pawn.
 *
 * Example: White knight f3 attacks a black pawn e5 that one knight defends.
 * Bringing a second attacker (d4, Bc4-d5 …) makes the pawn winnable; the
 * success check is material actually won, not squares visited.
 */
object WinWeakUnitTemplate : PlanTemplate {

    override val family = PlanFamily.WIN_WEAK_UNIT
    override val version = 1

    private const val MAX_TARGETS = 2
    private const val MAX_ACTIONS = 6
    private const val MAX_CHALLENGES = 8

    /** A target may be defended once more than it is attacked and still be worth pressing. */
    private const val MAX_DEFICIT = 1

    override fun bind(game: Game, owner: Side): List<PlanTarget> {
        val position = game.current
        val enemy = owner.opposite
        val enemyPins = position.pinnedPieces(enemy)

        return position.pieces(enemy)
            .filter { (_, piece) -> piece.type != PieceType.KING }
            .mapNotNull { (square, piece) ->
                val attackers = Facts.effectiveAttackers(position, square, owner).size
                val defenders = Facts.defenders(position, square).size
                val pinned = square in enemyPins

                // Already winnable now is a tactic, not a plan (Section 2.3).
                if ((Facts.bestCaptureSee(position, square, owner) ?: 0) > 0) return@mapNotNull null

                val pressed = attackers >= 1 && (pinned || defenders - attackers <= 0)
                val loose = attackers == 0 && defenders == 0 && piece.type.value >= PieceType.KNIGHT.value
                if (!pressed && !loose) return@mapNotNull null

                val priority = (if (pinned) 1000 else 0) + (attackers - defenders) * 100 + piece.type.value
                Triple(square, piece.type, priority)
            }
            .sortedWith(compareByDescending<Triple<Square, PieceType, Int>> { it.third }.thenBy { it.first.index })
            .take(MAX_TARGETS)
            .map { (square, type, _) -> PlanTarget(square, game.pieceIdAt(square), type) }
    }

    override fun locate(game: Game, plan: PlanInstance): Square? {
        val id = plan.target.pieceId ?: return null
        return game.squareOf(id)
    }

    override fun dependencies(game: Game, plan: PlanInstance): List<DependencyState> {
        val square = locate(game, plan)
            ?: return listOf(DependencyState(DependencyKind.TARGET_PRESENT, holds = false))

        val position = game.current
        val attackers = Facts.effectiveAttackers(position, square, plan.owner)
        val defenders = Facts.defenders(position, square)
        val pinned = square in position.pinnedPieces(plan.owner.opposite)
        val pressured = (attackers.isNotEmpty() || pinned) && defenders.size - attackers.size <= MAX_DEFICIT

        return listOf(
            DependencyState(DependencyKind.TARGET_PRESENT, holds = true, pieces = listOf(square)),
            DependencyState(DependencyKind.TARGET_UNDER_PRESSURE, holds = pressured, pieces = attackers),
        )
    }

    override fun actions(game: Game, plan: PlanInstance): List<PlanAction> {
        val position = game.current
        if (position.sideToMove != plan.owner) return emptyList()
        val target = locate(game, plan) ?: return emptyList()

        val enemy = plan.owner.opposite
        val attackersBefore = Facts.effectiveAttackers(position, target, plan.owner).size
        val defenders = Facts.defenders(position, target).toSet()
        val pinsBefore = position.pinnedPieces(enemy).keys

        val actions = position.legalMoves().mapNotNull { move ->
            if (move.to == target) {
                return@mapNotNull if (position.see(move) >= 0) PlanAction(move, Subgoal.STRIKE) else null
            }
            if (move.to in defenders && position.isCapture(move)) {
                return@mapNotNull if (position.see(move) >= 0) PlanAction(move, Subgoal.REMOVE_DEFENDER) else null
            }

            val after = position.play(move)
            val attackersAfter = Facts.effectiveAttackers(after, target, plan.owner).size
            val newPins = after.pinnedPieces(enemy).keys - pinsBefore
            val defenderNewlyAttacked = defenders.any { it in after.attackedSquares(move.to) && it !in position.attackedSquares(move.from) }

            when {
                attackersAfter < attackersBefore -> null
                attackersAfter > attackersBefore -> PlanAction(move, Subgoal.ADD_ATTACKER)
                target in newPins || newPins.any { it in defenders } -> PlanAction(move, Subgoal.PIN)
                defenderNewlyAttacked -> PlanAction(move, Subgoal.ATTACK_DEFENDER)
                else -> null
            }
        }

        return actions
            .sortedWith(compareByDescending<PlanAction> { PlanTemplate.advancement(it.subgoal) }.thenBy { it.move.uci })
            .take(MAX_ACTIONS)
    }

    override fun challenges(game: Game, plan: PlanInstance): List<Move> {
        val position = game.current
        if (position.sideToMove == plan.owner) return emptyList()
        val target = locate(game, plan) ?: return emptyList()

        val attackers = Facts.effectiveAttackers(position, target, plan.owner)
        val defendersBefore = Facts.defenders(position, target).size

        return position.legalMoves().filter { move ->
            if (move.from == target) return@filter true
            if (move.to in attackers) return@filter true
            val after = position.play(move)
            Facts.defenders(after, target).size > defendersBefore ||
                Facts.effectiveAttackers(after, target, plan.owner).size < attackers.size
        }.sortedBy { it.uci }.take(MAX_CHALLENGES)
    }

    override fun goal(game: Game, plan: PlanInstance): GoalState {
        if (locate(game, plan) != null) return GoalState.OPEN
        // The target is gone: the goal holds only if material was actually won.
        val gained = game.balance(plan.owner) - plan.startBalance
        return if (gained >= PieceType.PAWN.value) GoalState.REACHED else GoalState.FAILED
    }
}
