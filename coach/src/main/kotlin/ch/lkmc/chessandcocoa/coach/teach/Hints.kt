// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.teach

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.coach.analysis.GameSearch
import ch.lkmc.chessandcocoa.coach.evidence.MarkedSquare
import ch.lkmc.chessandcocoa.coach.evidence.SquareRole
import ch.lkmc.chessandcocoa.coach.facts.Facts
import ch.lkmc.chessandcocoa.coach.plan.PlanAction
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.plan.PlanTemplate
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.render.Renderer
import kotlinx.serialization.Serializable

/** Progressive hint levels (Section 4.2): each reveals more, and any hint marks the move as assisted. */
@Serializable
enum class HintLevel { THREAT, IDEA, MOVE }

@Serializable
data class Hint(
    val level: HintLevel,
    val text: String,
    val marks: List<MarkedSquare>,
    val suggestion: Move? = null,
)

/**
 * Voluntary, progressive hints. They read the recorded plan and the
 * analyst's view; they never change opponent strength (Section 4.4).
 */
object Hints {

    /**
     * [game] has the human to move; [plan] is the opponent's open episode,
     * [analysis] the analyst's root search of this position.
     */
    fun hint(game: Game, level: HintLevel, plan: PlanInstance?, analysis: GameSearch): Hint = when (level) {
        HintLevel.THREAT -> threat(game, plan)
        HintLevel.IDEA -> idea(game, plan, analysis)
        HintLevel.MOVE -> move(game, analysis)
    }

    private fun threat(game: Game, plan: PlanInstance?): Hint {
        val position = game.current
        val human = position.sideToMove
        val opponent = human.opposite

        if (plan != null && plan.isOpen) {
            val target = PlanTemplate.of(plan.family).locate(game, plan) ?: plan.current.targetSquare
            val attackers = Facts.effectiveAttackers(position, target, opponent)
            val marks = listOf(MarkedSquare(target, SquareRole.TARGET)) + attackers.map { MarkedSquare(it, SquareRole.ATTACKER) }
            val text = when (plan.family) {
                PlanFamily.ATTACK_KING_SQUARE -> "Keep an eye on $target, next to your king: my pieces are aiming at it."
                PlanFamily.WIN_WEAK_UNIT -> "Keep an eye on your ${Renderer.name(position.pieceAt(target)?.type)} on $target: I am putting pressure on it."
                PlanFamily.PROMOTE_PASSED_PAWN -> "Watch my passed pawn on $target: none of your pawns can stop it, and it wants to promote."
            }
            return Hint(HintLevel.THREAT, text, marks)
        }

        Facts.threatenedMates(position, opponent).firstOrNull()?.let { mate ->
            return Hint(HintLevel.THREAT, "Look out: I am threatening checkmate.", listOf(MarkedSquare(mate.to, SquareRole.THREAT)))
        }
        Facts.winningCaptures(position, opponent).firstOrNull()?.let { (capture, _) ->
            val piece = position.pieceAt(capture.to)
            return Hint(
                HintLevel.THREAT,
                "Your ${Renderer.name(piece?.type)} on ${capture.to} is not safe: I could win it.",
                listOf(MarkedSquare(capture.to, SquareRole.TARGET), MarkedSquare(capture.from, SquareRole.ATTACKER)),
            )
        }
        return Hint(HintLevel.THREAT, "Nothing of yours is in immediate danger. Look for a move that improves a piece or creates a threat.", emptyList())
    }

    private fun idea(game: Game, plan: PlanInstance?, analysis: GameSearch): Hint {
        val position = game.current
        val human = position.sideToMove

        nextStep(game, plan)?.let { step ->
            val san = position.passTurn()?.san(step.move) ?: step.move.uci
            return Hint(
                HintLevel.IDEA,
                "I am preparing $san. Can you stop it without creating a new problem?",
                listOf(MarkedSquare(step.move.from, SquareRole.ATTACKER), MarkedSquare(step.move.to, SquareRole.THREAT)),
            )
        }

        Facts.winningCaptures(position, human).firstOrNull()?.let { (capture, _) ->
            return Hint(HintLevel.IDEA, "There is material to win. Look at ${capture.to}.", listOf(MarkedSquare(capture.to, SquareRole.TARGET)))
        }

        val best = analysis.result.best?.let { Move.parseUci(it.move) }
            ?: return Hint(HintLevel.IDEA, "Take your time and compare a few candidate moves.", emptyList())
        val piece = position.pieceAt(best.from)
        return Hint(
            HintLevel.IDEA,
            "Consider what your ${Renderer.name(piece?.type)} on ${best.from} could do.",
            listOf(MarkedSquare(best.from, SquareRole.MOVED)),
        )
    }

    private fun move(game: Game, analysis: GameSearch): Hint {
        val line = analysis.result.best ?: return Hint(HintLevel.MOVE, "I have no suggestion here.", emptyList())
        val best = Move.parseUci(line.move)
        val san = game.current.san(best)
        return Hint(
            HintLevel.MOVE,
            "$san is a strong move here.",
            listOf(MarkedSquare(best.from, SquareRole.MOVED), MarkedSquare(best.to, SquareRole.MOVED)),
            suggestion = best,
        )
    }

    private fun nextStep(game: Game, plan: PlanInstance?): PlanAction? =
        plan?.takeIf { it.isOpen }?.let { PlanTemplate.nextStep(game, it) }
}
