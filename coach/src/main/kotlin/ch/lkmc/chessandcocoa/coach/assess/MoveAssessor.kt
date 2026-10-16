// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.assess

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Position
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.analysis.GameSearch
import ch.lkmc.chessandcocoa.coach.analysis.ScoreMath
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.coach.decide.CandidateValue
import ch.lkmc.chessandcocoa.coach.decide.DecisionRecord
import ch.lkmc.chessandcocoa.coach.decide.SelectionBasis
import ch.lkmc.chessandcocoa.coach.facts.Facts
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.engine.Score

/**
 * Assesses a human move on two independent axes (concept Section 10):
 * overall quality against strong alternatives, and the effect on the
 * opponent's recorded plan. The analyst budget here never depends on the
 * opponent's difficulty (R9).
 */
class MoveAssessor(private val searcher: Searcher, private val budgets: AnalystBudgets) {

    private val impactAssessor = PlanImpactAssessor(searcher, budgets)

    /** The root analysis of a human decision; safe to run early, while the human thinks (Section 14.2). */
    suspend fun analyzeDecision(parent: Game): GameSearch =
        searcher.search(parent, budgets.assessNodes, multiPv = minOf(budgets.assessMultiPv, parent.current.legalMoves().size))

    /**
     * [prior] is the opponent's decision that led to [parent], with [priorPlan]
     * its plan as recorded then; [planWindowCp] is that plan's policy window.
     */
    suspend fun assess(
        parent: Game,
        move: Move,
        prior: DecisionRecord?,
        priorPlan: PlanInstance?,
        planWindowCp: Int,
        assisted: Boolean,
    ): MoveAssessment {
        val searchesBefore = searcher.searchCount
        val nodesBefore = searcher.nodeCount
        val position = parent.current
        val side = position.sideToMove

        val root = analyzeDecision(parent)
        val played = playedLine(parent, root, move)
        val quality = quality(parent, root, move, played)
        val after = parent.play(move)

        val tactics = tactics(parent, after, root, move, quality, played, prior)
        val impact = priorPlan?.takeIf { it.isOpen && after.outcome() == null }?.let {
            impactAssessor.assess(parent, move, it, prior, root, planWindowCp)
        }

        val alternatives = root.result.lines.mapIndexed { index, line ->
            val candidate = Move.parseUci(line.move)
            CandidateValue(candidate, position.san(candidate), root.score(line, side), line.bound, line.depth, index + 1)
        }.filter { it.move != move }

        // "Another defense avoids both problems" (Section 10.4): a stronger move that also addresses the plan.
        val costlyDefense = impact != null && impact.addressed && quality.category >= QualityCategory.MISTAKE
        val betterDefense = if (costlyDefense && priorPlan != null) {
            alternatives.firstOrNull { it.score.rank() > quality.playedScore.rank() && impactAssessor.addresses(parent, it.move, priorPlan) }
        } else {
            null
        }

        return MoveAssessment(
            id = "a-${parent.ply}",
            ply = parent.ply,
            side = side,
            move = move,
            san = position.san(move),
            quality = quality,
            tactics = tactics,
            planImpact = impact,
            alternatives = alternatives,
            betterDefense = betterDefense,
            assisted = assisted,
            searches = searcher.searchCount - searchesBefore,
            nodes = searcher.nodeCount - nodesBefore,
        )
    }

    /** The played move's line, from the shared root search or a restricted one at the same budget. */
    private suspend fun playedLine(parent: Game, root: GameSearch, move: Move): PlayedLine {
        val side = parent.current.sideToMove
        root.lineFor(move)?.let { return PlayedLine(root.score(it, side), it.pv.map(Move::parseUci), separate = false) }
        val restricted = searcher.search(parent, budgets.assessNodes, searchMoves = listOf(move))
        val line = restricted.result.best ?: return PlayedLine(root.bestScore(side) ?: Score.Centipawns(0), emptyList(), separate = true)
        return PlayedLine(restricted.score(line, side), line.pv.map(Move::parseUci), separate = true)
    }

    private data class PlayedLine(val score: Score, val pv: List<Move>, val separate: Boolean)

    private fun quality(parent: Game, root: GameSearch, move: Move, played: PlayedLine): QualityAssessment {
        val position = parent.current
        val side = position.sideToMove
        val bestLine = root.result.lines.first()
        val bestMove = Move.parseUci(bestLine.move)
        val bestScore = root.score(bestLine, side)
        val playedScore = played.score
        val separate = played.separate

        // A separate search that rates the move above the best one contradicts it: no loss, lower confidence.
        val contradicts = separate && playedScore.rank() > bestScore.rank()
        val category = if (move == bestMove || contradicts) QualityCategory.BEST else categorize(bestScore, playedScore)
        val loss = if (contradicts) 0 else ScoreMath.lossCp(bestScore, playedScore)

        return QualityAssessment(
            category = category,
            lossCp = loss,
            playedScore = playedScore,
            bestScore = bestScore,
            bestMove = bestMove,
            bestSan = position.san(bestMove),
            confidence = if (contradicts) Confidence.REDUCED else Confidence.FULL,
        )
    }

    private fun tactics(
        parent: Game,
        after: Game,
        root: GameSearch,
        move: Move,
        quality: QualityAssessment,
        playedLine: PlayedLine,
        prior: DecisionRecord?,
    ): List<TacticalFinding> {
        val position = parent.current
        val side = position.sideToMove
        val next = after.current
        val findings = mutableListOf<TacticalFinding>()

        if (next.isCheckmate()) return listOf(TacticalFinding.DeliversMate(position.san(move)))

        // Rule-verified: an immediate mate for the opponent.
        Facts.mateInOneMoves(next).firstOrNull()?.let { mate ->
            val matingPiece = next.pieceAt(mate.from)!!
            val supporters = next.attackers(mate.to, side.opposite).filter { it != mate.from }
            findings += TacticalFinding.AllowsMateInOne(mate, next.san(mate), matingPiece, supporters)
        }

        // Analysis-supported: a longer forced mate.
        val played = quality.playedScore
        if (findings.isEmpty() && played is Score.Mate && played.moves < 0 && !ScoreMath.isLosingMate(quality.bestScore)) {
            val line = playedLine.pv
            findings += TacticalFinding.AllowsForcedMate(-played.moves, line, sanLine(position, line))
        }

        exposedPiece(after, playedLine, quality)?.let { findings += it }
        missedWin(parent, root, quality)?.let { findings += it }

        if (position.isCapture(move) && position.see(move) > 0 && quality.category <= QualityCategory.GOOD) {
            val captured = position.capturedPiece(move)!!
            findings += TacticalFinding.WinsMaterial(captured, position.capturedSquare(move)!!, position.see(move))
        }

        val threats = Facts.threatenedMates(position, side.opposite)
        if (threats.isNotEmpty() && Facts.mateInOneMoves(next).isEmpty()) {
            val threat = threats.first()
            val passed = position.passTurn()
            findings += TacticalFinding.StopsMateThreat(threat, passed?.san(threat) ?: threat.uci)
        }

        oversight(parent, move, quality, prior)?.let { findings += it }
        return findings
    }

    /** The engine's expected reply captures a piece the move left exposed. */
    private fun exposedPiece(after: Game, playedLine: PlayedLine, quality: QualityAssessment): TacticalFinding.LeavesPieceExposed? {
        val loss = quality.lossCp ?: return null
        if (loss < EXPOSED_LOSS_CP) return null
        val reply = playedLine.pv.getOrNull(1) ?: return null

        val next = after.current
        if (!next.isLegal(reply) || !next.isCapture(reply) || next.see(reply) <= 0) return null
        val captured = next.capturedPiece(reply) ?: return null
        if (captured.type.value < PieceType.KNIGHT.value) return null

        val square = next.capturedSquare(reply)!!
        val undefended = Facts.defenders(next, square).isEmpty()
        return TacticalFinding.LeavesPieceExposed(square, captured, reply, next.san(reply), undefended)
    }

    private fun missedWin(parent: Game, root: GameSearch, quality: QualityAssessment): TacticalFinding.MissedWin? {
        val loss = quality.lossCp
        val position = parent.current
        val best = quality.bestMove
        if (quality.category < QualityCategory.MISTAKE) return null

        if (ScoreMath.isWinningMate(quality.bestScore) && !ScoreMath.isWinningMate(quality.playedScore)) {
            val line = root.result.best?.pv?.map(Move::parseUci).orEmpty()
            return TacticalFinding.MissedWin(best, quality.bestSan, mate = true, captured = null, line = line)
        }
        if (loss != null && position.isCapture(best) && position.see(best) >= PieceType.KNIGHT.value) {
            return TacticalFinding.MissedWin(best, quality.bestSan, mate = false, captured = position.capturedPiece(best), line = listOf(best))
        }
        return null
    }

    /**
     * The opponent's last decision expected far more than it now gets: its
     * own (difficulty-limited) search did not see this move. Concessions are
     * excluded: those were deliberate, and are disclosed in the review.
     */
    private fun oversight(parent: Game, move: Move, quality: QualityAssessment, prior: DecisionRecord?): TacticalFinding.OpponentOversight? {
        if (prior == null || prior.selectionBasis == SelectionBasis.DIFFICULTY_CONCESSION) return null
        if (quality.category > QualityCategory.GOOD) return null

        val position = parent.current
        val strikes = position.play(move).isCheckmate() || (position.isCapture(move) && position.see(move) >= PieceType.KNIGHT.value)
        if (!strikes) return null

        val expected = prior.candidates.firstOrNull { it.move == prior.move }?.score ?: return null
        val actual = quality.bestScore.negate()
        val drop = ScoreMath.clampedCp(expected) - ScoreMath.clampedCp(actual)
        return if (drop >= OVERSIGHT_DROP_CP) TacticalFinding.OpponentOversight(prior.id, expected, actual) else null
    }

    private fun sanLine(position: Position, moves: List<Move>): List<String> {
        var current = position
        return moves.map { move ->
            val san = current.san(move)
            current = current.play(move)
            san
        }
    }

    internal companion object {
        // Uncalibrated starting bands (concept Section 10.1).
        const val BEST_CP = 15
        const val GOOD_CP = 50
        const val INACCURACY_CP = 120
        const val MISTAKE_CP = 300

        /** Beyond this the game is decided either way; small differences then matter less. */
        const val DECIDED_CP = 400
        const val EXPOSED_LOSS_CP = 150
        const val OVERSIGHT_DROP_CP = 200
        const val MATE_SLACK_MOVES = 3

        fun categorize(best: Score, played: Score): QualityCategory {
            val bestMate = ScoreMath.isWinningMate(best)
            val playedMate = ScoreMath.isWinningMate(played)
            val playedLosing = ScoreMath.isLosingMate(played)
            val bestLosing = ScoreMath.isLosingMate(best)

            return when {
                bestMate && playedMate ->
                    if ((played as Score.Mate).moves > (best as Score.Mate).moves + MATE_SLACK_MOVES) QualityCategory.GOOD else QualityCategory.BEST
                bestMate -> if (ScoreMath.clampedCp(played) >= DECIDED_CP) QualityCategory.MISTAKE else QualityCategory.BLUNDER
                bestLosing -> QualityCategory.BEST
                playedLosing -> QualityCategory.BLUNDER
                else -> byLoss((best as Score.Centipawns).cp, (played as Score.Centipawns).cp)
            }
        }

        private fun byLoss(best: Int, played: Int): QualityCategory {
            val loss = best - played
            val raw = when {
                loss <= BEST_CP -> QualityCategory.BEST
                loss <= GOOD_CP -> QualityCategory.GOOD
                loss <= INACCURACY_CP -> QualityCategory.INACCURACY
                loss <= MISTAKE_CP -> QualityCategory.MISTAKE
                else -> QualityCategory.BLUNDER
            }
            val decided = (best >= DECIDED_CP && played >= DECIDED_CP) || (best <= -DECIDED_CP && played <= -DECIDED_CP)
            return if (decided && raw > QualityCategory.INACCURACY) QualityCategory.INACCURACY else raw
        }
    }
}
