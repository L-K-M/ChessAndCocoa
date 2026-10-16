// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach

import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.chess.Square
import ch.lkmc.chessandcocoa.coach.analysis.AnalystBudgets
import ch.lkmc.chessandcocoa.coach.assess.ImpactKind
import ch.lkmc.chessandcocoa.coach.assess.MoveAssessment
import ch.lkmc.chessandcocoa.coach.assess.MoveAssessor
import ch.lkmc.chessandcocoa.coach.assess.QualityCategory
import ch.lkmc.chessandcocoa.coach.assess.TacticalFinding
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.evidence.Claim
import ch.lkmc.chessandcocoa.coach.evidence.ClaimBuilder
import ch.lkmc.chessandcocoa.coach.evidence.ClaimVerifier
import ch.lkmc.chessandcocoa.coach.plan.AttackKingSquareTemplate
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.plan.PlanInstance
import ch.lkmc.chessandcocoa.coach.plan.PlanStatus
import ch.lkmc.chessandcocoa.coach.plan.Planner
import ch.lkmc.chessandcocoa.coach.plan.TransitionReason
import ch.lkmc.chessandcocoa.coach.render.Renderer
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import ch.lkmc.chessandcocoa.coach.teach.TeachingPolicy
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The concept's rules-level fixture (Section 13.2): 1.e4 e5 2.Bc4 Nc6 3.Qh5,
 * computer White, with a prior record in which White chose the f7 threat.
 * The record is a test setup, as the concept prescribes.
 */
class ScholarFixtureTest {

    private val parent = gameOf("e4", "e5", "Bc4", "Nc6", "Qh5")

    @Test
    fun responseAAllowsMateAndTheClaimNamesItsSupport() = runBlocking {
        val assessment = assess("Nf6")

        val mate = assessment.tactics.filterIsInstance<TacticalFinding.AllowsMateInOne>().single()
        assertEquals("Qxf7#", mate.mateSan)
        assertTrue(Square.parse("c4") in mate.supporters)
        assertEquals(QualityCategory.BLUNDER, assessment.quality.category)

        // Plan axis: the move ignores the plan, whose next step (the mate) stays available.
        assertEquals(listOf(ImpactKind.ALLOWS), assessment.planImpact!!.impacts.filter { it != ImpactKind.CREATES_URGENCY })

        val claims = claimsFor(assessment)
        val allows = claims.filterIsInstance<Claim.AllowsMate>().single()
        assertEquals(listOf("h5f7"), allows.continuation.moves.map { it.uci })
        assertEquals(listOf(Square.parse("c4") to PieceType.BISHOP), allows.supporters)
        assertEquals(
            "That allows checkmate: Qxf7#. My queen would be protected by the bishop on c4.",
            Renderer.render(allows),
        )

        // The serious point comes first; the plan becomes the expansion (Section 4.3).
        val event = TeachingPolicy.select(claims, ExperiencePreferences(), "e", parent.ply + 1, 1)!!
        assertTrue(event.claims.first() is Claim.AllowsMate)
    }

    @Test
    fun responseBDelaysTheGoalButDoesNotPreventIt() = runBlocking {
        val assessment = assess("g6")
        val impact = assessment.planImpact!!

        assertTrue(impact.addressed)
        assertTrue(ImpactKind.DELAYS in impact.impacts, "impacts were ${impact.impacts}")
        assertFalse(ImpactKind.PREVENTS in impact.impacts)
        assertTrue(assessment.tactics.none { it is TacticalFinding.AllowsMateInOne })
        assertTrue(assessment.tactics.any { it is TacticalFinding.StopsMateThreat })
        // The plan renews itself with a queen move that aims at f7 again (4.Qf3 in the concept's check).
        assertEquals(PlanFamily.ATTACK_KING_SQUARE, impact.family)
        assertTrue(impact.played.action != null)
    }

    @Test
    fun stoppingOnlyTheImmediateMateIsNeitherPreventionNorFavorable() = runBlocking {
        // 3...Qh4 leaves no mate in one but loses to 4.Qxf7+ Kd8 5.Qxf8#.
        val assessment = assess("Qh4")
        assertTrue(assessment.tactics.none { it is TacticalFinding.AllowsMateInOne })
        assertFalse(ImpactKind.PREVENTS in assessment.planImpact!!.impacts)
        assertEquals(QualityCategory.BLUNDER, assessment.quality.category)
    }

    @Test
    fun everyDisplayedClaimReplaysFromItsOwnState() = runBlocking {
        for (reply in listOf("Nf6", "g6", "Qe7", "Qh4", "a6")) {
            val assessment = assess(reply)
            val claims = claimsFor(assessment)
            val after = parent.play(parent.move(reply))
            assertEquals(claims, ClaimVerifier.verified(claims, after), "a claim for $reply failed verification")
        }
    }

    private suspend fun assess(san: String): MoveAssessment {
        val assessor = MoveAssessor(TestEngine.searcher(), AnalystBudgets())
        return assessor.assess(parent, parent.move(san), prior = null, priorPlan = plan(), planWindowCp = Difficulty.STEADY.planWindowCp, assisted = false)
    }

    private fun claimsFor(assessment: MoveAssessment): List<Claim> =
        ClaimBuilder.forHumanMove(parent, assessment, prior = null, priorPlan = plan(), closedEpisodes = emptyList())

    /** The fixture's prior intention: White chose Qh5 to threaten f7. */
    private fun plan(): PlanInstance {
        val beforeQh5 = parent.truncate(4)
        val proposed = Planner.propose(beforeQh5, Side.WHITE, emptyList()).first { it.family == PlanFamily.ATTACK_KING_SQUARE }
        val action = AttackKingSquareTemplate.actions(beforeQh5, proposed).first { beforeQh5.current.san(it.move) == "Qh5" }
        return proposed.revise(beforeQh5.ply, PlanStatus.ACTIVE, TransitionReason.ADOPTED, action = action)
    }
}
