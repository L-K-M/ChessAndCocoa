// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.evidence

import ch.lkmc.chessandcocoa.chess.Game
import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.assess.ImpactKind
import ch.lkmc.chessandcocoa.coach.assess.MoveAssessment
import ch.lkmc.chessandcocoa.coach.assess.QualityCategory
import ch.lkmc.chessandcocoa.coach.assess.TacticalFinding
import ch.lkmc.chessandcocoa.coach.decide.DecisionRecord
import ch.lkmc.chessandcocoa.coach.decide.SelectionBasis
import ch.lkmc.chessandcocoa.coach.facts.Facts
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus

/**
 * Turns assessments and frozen decision records into evidence-bearing
 * claims (concept Section 12.1). Continuations always start from the exact
 * game state they describe; a claim whose evidence cannot be built is left
 * out rather than filled with speculation (R8).
 */
object ClaimBuilder {

    /** Claims about a human move: [parent] is the game before it, [assessment] its two-axis result. */
    fun forHumanMove(
        parent: Game,
        assessment: MoveAssessment,
        prior: DecisionRecord?,
        priorPlan: PlanInstance?,
        closedEpisodes: List<PlanInstance>,
    ): List<Claim> {
        val after = parent.play(assessment.move)
        val claims = mutableListOf<Claim>()
        val moved = listOf(MarkedSquare(assessment.move.from, SquareRole.MOVED), MarkedSquare(assessment.move.to, SquareRole.MOVED))

        after.outcome()?.let { outcome -> claims += Claim.GameOver(outcome.winner, outcome.reason, assessment.side, moved) }

        assessment.tactics.forEach { finding -> tacticClaim(parent, after, assessment, finding)?.let { claims += it } }

        claims += Claim.MoveQuality(
            category = assessment.quality.category,
            bestSan = assessment.quality.bestSan,
            playedWasBest = assessment.quality.bestMove == assessment.move,
            lossCp = assessment.quality.lossCp,
            marks = listOf(MarkedSquare(assessment.quality.bestMove.from, SquareRole.MOVED), MarkedSquare(assessment.quality.bestMove.to, SquareRole.MOVED)),
            continuation = continuation(parent, listOf(assessment.quality.bestMove)),
        )

        planClaims(parent, after, assessment, prior, priorPlan)?.let { claims += it }

        assessment.betterDefense?.let { defense ->
            claims += Claim.BetterDefense(defense.san, listOf(MarkedSquare(defense.move.from, SquareRole.MOVED), MarkedSquare(defense.move.to, SquareRole.MOVED)))
        }

        // Revealed plans that ended because of this move are narrated as transitions (R7).
        closedEpisodes.filter { it.episodeId == priorPlan?.episodeId }.forEach { episode ->
            claims += Claim.PlanTransitionClaim(episode.family, episode.current.targetSquare, episode.current.reason)
        }
        return claims
    }

    /** Review-only claims: every deliberate concession, disclosed after the game (Section 9.3). */
    fun concessions(game: Game, decisions: List<DecisionRecord>): List<Claim> = decisions
        .filter { it.selectionBasis == SelectionBasis.DIFFICULTY_CONCESSION }
        .mapNotNull { decision ->
            val position = game.positions.getOrNull(decision.ply) ?: return@mapNotNull null
            Claim.Concession(decision.ply, position.fullmoveNumber, decision.san, decision.candidates.firstOrNull()?.san ?: decision.san)
        }

    private fun tacticClaim(parent: Game, after: Game, assessment: MoveAssessment, finding: TacticalFinding): Claim? = when (finding) {
        is TacticalFinding.AllowsMateInOne -> Claim.AllowsMate(
            mateSan = finding.mateSan,
            matingPiece = finding.matingPiece,
            supporters = finding.supporters.mapNotNull { square -> after.current.pieceAt(square)?.let { square to it.type } },
            marks = listOf(MarkedSquare(finding.mateMove.from, SquareRole.ATTACKER), MarkedSquare(finding.mateMove.to, SquareRole.TARGET)) +
                finding.supporters.map { MarkedSquare(it, SquareRole.ATTACKER) },
            continuation = continuation(after, listOf(finding.mateMove)),
        )

        is TacticalFinding.AllowsForcedMate -> finding.line.drop(1).takeIf { it.isNotEmpty() }?.let { reply ->
            Claim.AllowsForcedMate(
                mateIn = finding.mateIn,
                marks = listOf(MarkedSquare(reply.first().from, SquareRole.ATTACKER), MarkedSquare(reply.first().to, SquareRole.TARGET)),
                continuation = continuation(after, reply),
            )
        }

        is TacticalFinding.LeavesPieceExposed -> Claim.ExposesPiece(
            piece = finding.piece.type,
            square = finding.square,
            undefended = finding.undefended,
            captureSan = finding.captureSan,
            marks = listOf(MarkedSquare(finding.square, SquareRole.TARGET), MarkedSquare(finding.capture.from, SquareRole.ATTACKER)),
            continuation = continuation(after, listOf(finding.capture)),
            alreadyExposed = finding.alreadyExposed,
            defense = finding.recaptureLine.takeIf { it.isNotEmpty() }?.let { continuation(parent, it) },
        )

        is TacticalFinding.MissedWin -> Claim.MissedWin(
            san = finding.san,
            mate = finding.mate,
            captured = finding.captured?.type,
            marks = listOf(MarkedSquare(finding.move.from, SquareRole.MOVED), MarkedSquare(finding.move.to, SquareRole.TARGET)),
            continuation = continuation(parent, finding.line.ifEmpty { listOf(finding.move) }),
        )

        is TacticalFinding.WinsMaterial -> Claim.WinsMaterial(finding.captured.type, finding.square, listOf(MarkedSquare(finding.square, SquareRole.TARGET)))

        is TacticalFinding.StopsMateThreat -> Claim.StopsMateThreat(
            threatSan = finding.threatSan,
            marks = listOf(MarkedSquare(finding.threat.to, SquareRole.THREAT), MarkedSquare(assessment.move.to, SquareRole.DEFENDER)),
        )

        is TacticalFinding.OpponentOversight -> Claim.OpponentOversight(listOf(MarkedSquare(assessment.move.to, SquareRole.MOVED)))

        is TacticalFinding.DeliversMate -> null // covered by GameOver
    }

    /**
     * The opponent's recorded intention and the move's effect on it. The
     * intention comes only from the frozen decision record, never from the
     * current board, so it cannot be invented after the fact (Section 10.5).
     */
    private fun planClaims(
        parent: Game,
        after: Game,
        assessment: MoveAssessment,
        prior: DecisionRecord?,
        priorPlan: PlanInstance?,
    ): List<Claim>? {
        val impact = assessment.planImpact ?: return null
        val decision = prior ?: return null
        val plan = priorPlan ?: return null
        if (plan.status != PlanStatus.ACTIVE) return null

        // Nothing to say about a plan the move did not touch in a supported way.
        val material = impact.impacts.any { it != ImpactKind.NO_SUPPORTED_MATERIAL_EFFECT && it != ImpactKind.UNKNOWN }
        if (!material) return null

        val target = impact.targetSquare
        val action = plan.current.action
        val revealed = Claim.PlanRevealed(
            family = plan.family,
            target = target,
            targetPiece = plan.target.pieceType,
            episodeId = plan.episodeId,
            decisionId = decision.id,
            intendedSan = impact.threatSan,
            intendedSubgoal = impact.threat?.subgoal,
            planWasReason = decision.planIsReason,
            recordedAtPly = decision.ply,
            marks = listOfNotNull(
                MarkedSquare(target, SquareRole.TARGET),
                action?.let { MarkedSquare(it.move.to, SquareRole.ATTACKER) },
            ) + attackersOf(parent, target, decision.side),
        )

        val followUp = impact.played.action
        val effect = Claim.PlanImpact(
            family = plan.family,
            target = target,
            impacts = impact.impacts,
            threatSan = impact.threatSan,
            followUpSan = impact.played.actionSan,
            followUpSubgoal = followUp?.subgoal,
            anticipated = impact.anticipated,
            marks = listOfNotNull(
                MarkedSquare(target, SquareRole.TARGET),
                MarkedSquare(assessment.move.to, SquareRole.MOVED),
                followUp?.let { MarkedSquare(it.move.to, SquareRole.THREAT) },
            ),
            continuation = followUp?.let { continuation(after, listOf(it.move)) },
        )
        return listOf(revealed, effect)
    }

    private fun attackersOf(game: Game, target: Square, side: Side): List<MarkedSquare> =
        Facts.effectiveAttackers(game.current, target, side).map { MarkedSquare(it, SquareRole.ATTACKER) }

    /** A continuation from the end of [game], with SAN computed by replaying it. */
    fun continuation(game: Game, moves: List<Move>): Continuation {
        var position = game.current
        val sans = moves.map { move ->
            val san = position.san(move)
            position = position.play(move)
            san
        }
        return Continuation(game.ply, game.current.fen(), moves, sans)
    }

    /** Claims considered serious enough to state first and plainly (Section 4.3). */
    fun isSerious(claim: Claim): Boolean = when (claim) {
        is Claim.AllowsMate, is Claim.AllowsForcedMate, is Claim.ExposesPiece -> true
        is Claim.MoveQuality -> claim.category >= QualityCategory.MISTAKE
        is Claim.MissedWin -> true
        else -> false
    }
}
