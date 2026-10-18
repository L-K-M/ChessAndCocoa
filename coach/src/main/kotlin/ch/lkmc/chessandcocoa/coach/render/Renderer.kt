// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.render

import ch.lkmc.chessandcocoa.chess.OutcomeReason
import ch.lkmc.chessandcocoa.chess.PieceType
import ch.lkmc.chessandcocoa.coach.decide.DecisionRecord
import ch.lkmc.chessandcocoa.coach.decide.Obligation
import ch.lkmc.chessandcocoa.coach.decide.SelectionBasis
import ch.lkmc.chessandcocoa.coach.assess.ImpactKind
import ch.lkmc.chessandcocoa.coach.assess.QualityCategory
import ch.lkmc.chessandcocoa.coach.evidence.Claim
import ch.lkmc.chessandcocoa.coach.plan.PlanFamily
import ch.lkmc.chessandcocoa.coach.plan.TransitionReason

/** Whether numeric detail may appear (Section 4.4: off by default). */
enum class NumberDetail { HIDDEN, SHOWN }

/**
 * Deterministic English templates over typed claims (concept Section 12.2),
 * in the Chess & Cocoa voice (Section 4.6): warm, plain, concrete; ordinary
 * chess words; no praise for routine moves; serious consequences said plainly.
 * Wording never adds a participant, consequence or certainty the claim lacks.
 */
object Renderer {

    /** Present-tense intent from a verified frozen decision, never from later analysis. */
    fun intent(decision: DecisionRecord): String {
        val plan = decision.plan
        if (plan != null) {
            val target = plan.current.targetSquare
            val goal = when (plan.family) {
                PlanFamily.ATTACK_KING_SQUARE -> "build an attack on $target near your king"
                PlanFamily.WIN_WEAK_UNIT -> "try to win your ${name(plan.target.pieceType)} on $target"
                PlanFamily.PROMOTE_PASSED_PAWN -> "help my passed pawn toward promotion"
                PlanFamily.INVADE_OPEN_FILE -> "bring a rook through an open file toward your pawn on $target"
            }
            return if (decision.planIsReason) "I chose this move to $goal." else "This move also fits my plan to $goal."
        }

        return when (decision.selectionBasis) {
            SelectionBasis.IMMEDIATE_OBLIGATION -> when {
                Obligation.FORCED_MOVE in decision.obligations -> "This was my only legal move."
                Obligation.IN_CHECK in decision.obligations -> "I need to get my king out of check."
                else -> "I am responding to a threat before pursuing a longer plan."
            }
            SelectionBasis.TACTICAL_OPPORTUNITY -> "I chose a tactical opportunity rather than a longer plan."
            // Deliberate concessions are disclosed only in review, not disguised as plans.
            else -> "I have no longer plan to explain for this move."
        }
    }

    fun render(claim: Claim, numbers: NumberDetail = NumberDetail.HIDDEN): String = when (claim) {
        is Claim.GameOver -> gameOver(claim)
        is Claim.AllowsMate -> allowsMate(claim)
        is Claim.AllowsForcedMate -> "That allows a forced checkmate in ${claim.mateIn}: ${claim.continuation.sans.joinToString(" ")}."
        is Claim.ExposesPiece -> exposes(claim)
        is Claim.MissedWin ->
            if (claim.mate) "There was a checkmate available: ${claim.san}."
            else "${claim.san} would have won my ${name(claim.captured)}."
        is Claim.WinsMaterial -> "You win my ${name(claim.captured)} on ${claim.square}."
        is Claim.StopsMateThreat -> "Your move stops ${claim.threatSan}, which would have been checkmate."
        is Claim.MoveQuality -> quality(claim, numbers)
        is Claim.PlanRevealed -> revealed(claim)
        is Claim.PlanImpact -> impact(claim)
        is Claim.BetterDefense -> "${claim.san} would also have dealt with my plan, without that cost."
        is Claim.OpponentOversight -> "I did not see that coming."
        is Claim.PlanTransitionClaim -> transition(claim)
        is Claim.Concession ->
            "On move ${claim.fullmoveNumber} I played ${claim.san} on purpose instead of the stronger ${claim.bestSan}, to keep the game gentle."
    }

    private fun gameOver(claim: Claim.GameOver): String = when (claim.reason) {
        OutcomeReason.CHECKMATE ->
            if (claim.winner == claim.humanSide) "Checkmate. You win this game."
            else "Checkmate. I win this game. You can look back over it, or take back moves and try another defense."
        OutcomeReason.STALEMATE -> "Stalemate: the side to move has no legal move and is not in check, so the game is a draw."
        OutcomeReason.INSUFFICIENT_MATERIAL -> "Neither side has enough pieces left to checkmate, so the game is a draw."
        OutcomeReason.THREEFOLD_REPETITION -> "The same position has come up three times, so the game is a draw."
        OutcomeReason.FIFTY_MOVE_RULE -> "Fifty moves passed without a capture or a pawn move, so the game is a draw."
        OutcomeReason.RESIGNATION ->
            if (claim.winner == claim.humanSide) "I resign. You win this game." else "You resigned. I win this game."
    }

    private fun allowsMate(claim: Claim.AllowsMate): String {
        val base = "That allows checkmate: ${claim.mateSan}."
        val support = claim.supporters.firstOrNull() ?: return base
        return "$base My ${name(claim.matingPiece.type)} would be protected by the ${name(support.second)} on ${support.first}."
    }

    private fun exposes(claim: Claim.ExposesPiece): String {
        val piece = "${name(claim.piece)} on ${claim.square}"
        val capture = if (claim.undefended) "I can capture it next: ${claim.captureSan}." else "I can win it with ${claim.captureSan}."
        val consequence = when {
            claim.alreadyExposed -> "Your $piece was already in danger, and this move does not save it. $capture"
            claim.undefended -> "That move leaves your $piece undefended. $capture"
            else -> "That move leaves your $piece without enough defenders. $capture"
        }
        val defense = claim.defense?.sans ?: return consequence
        return "$consequence ${defense[0]} would let you recapture: after ${defense[1]}, you could play ${defense[2]}."
    }

    private fun quality(claim: Claim.MoveQuality, numbers: NumberDetail): String {
        val detail = if (numbers == NumberDetail.SHOWN && claim.lossCp != null && claim.lossCp > 0) {
            " (about ${pawns(claim.lossCp)} by the engine's estimate)"
        } else {
            ""
        }
        return when (claim.category) {
            QualityCategory.BEST -> if (claim.playedWasBest) "That is the move I would have chosen too." else "That is as strong as any move here."
            QualityCategory.GOOD -> "A sound move. ${claim.bestSan} was a little more precise$detail."
            QualityCategory.INACCURACY -> "${claim.bestSan} was stronger$detail."
            QualityCategory.MISTAKE -> "That costs you something. ${claim.bestSan} was clearly stronger$detail."
            QualityCategory.BLUNDER -> "That is a serious mistake. ${claim.bestSan} was much stronger$detail."
        }
    }

    private fun revealed(claim: Claim.PlanRevealed): String {
        val aim = when (claim.family) {
            PlanFamily.ATTACK_KING_SQUARE -> "I was building an attack on ${claim.target}, a square only your king defended"
            PlanFamily.WIN_WEAK_UNIT -> "I was trying to win your ${name(claim.targetPiece)} on ${claim.target}"
            PlanFamily.PROMOTE_PASSED_PAWN -> "I was escorting my passed pawn on ${claim.target} toward promotion"
            PlanFamily.INVADE_OPEN_FILE -> "I was bringing a rook through an open file to win your pawn on ${claim.target}"
        }
        val step = claim.intendedSan?.let { " and was preparing $it" } ?: ""
        val role = if (claim.planWasReason) " That is why I played my last move." else " My last move also fit that idea."
        return "$aim$step.$role"
    }

    private fun impact(claim: Claim.PlanImpact): String {
        val parts = mutableListOf<String>()
        if (claim.anticipated) parts += "I had considered this reply."

        val impacts = claim.impacts.toSet()
        when {
            ImpactKind.ALLOWS in impacts ->
                parts += "Your move doesn't stop my plan against ${claim.target}." +
                    (claim.followUpSan?.let { " $it is now possible for me." } ?: "")
            ImpactKind.PREVENTS in impacts ->
                parts += "Your move stops my plan against ${claim.target}: I no longer have a good way to continue it."
            ImpactKind.DELAYS in impacts ->
                parts += (claim.threatSan?.let { "Your move stops $it for now." } ?: "Your move slows my plan down.") +
                    " I can still aim at ${claim.target}, but it takes more preparation" +
                    (claim.followUpSan?.let { ", for example with $it." } ?: ".")
            ImpactKind.ENABLES_OR_ACCELERATES in impacts ->
                parts += "Your move makes my plan easier" + (claim.followUpSan?.let { ": $it is now possible." } ?: ".")
        }
        if (ImpactKind.FORCES_ADAPTATION in impacts && ImpactKind.PREVENTS !in impacts) {
            parts += "I will need a different route toward ${claim.target}."
        }
        if (ImpactKind.REDUCES_BENEFIT in impacts) parts += "My plan still works, but it gains less now."
        if (ImpactKind.CREATES_URGENCY in impacts) parts += "You have also created a threat I need to deal with first."
        return parts.joinToString(" ")
    }

    private fun transition(claim: Claim.PlanTransitionClaim): String = when (claim.reason) {
        TransitionReason.GOAL_REACHED -> "My plan against ${claim.target} has worked."
        TransitionReason.TARGET_LOST, TransitionReason.DEPENDENCY_BROKEN ->
            "My plan against ${claim.target} no longer works, so I am dropping it."
        TransitionReason.STALLED -> "My plan against ${claim.target} is not getting anywhere, so I am letting it go."
        TransitionReason.OBLIGATION_TOOK_PRIORITY -> "I have something more urgent to handle before my plan against ${claim.target}."
        TransitionReason.BETTER_PLAN -> "I am switching to a more promising plan."
        else -> "I can't continue my plan against ${claim.target} without a cost, so I am setting it aside."
    }

    fun name(type: PieceType?): String = when (type) {
        PieceType.PAWN -> "pawn"
        PieceType.KNIGHT -> "knight"
        PieceType.BISHOP -> "bishop"
        PieceType.ROOK -> "rook"
        PieceType.QUEEN -> "queen"
        PieceType.KING -> "king"
        null -> "piece"
    }

    /** "12. Nf3" for White's move, "12... Nf6" for Black's, using the game's own move numbers. */
    fun moveLabel(fullmoveNumber: Int, whiteMoved: Boolean, san: String): String =
        if (whiteMoved) "$fullmoveNumber. $san" else "$fullmoveNumber... $san"

    private fun pawns(cp: Int): String {
        val tenths = (cp + 5) / 10
        val whole = tenths / 10
        val fraction = tenths % 10
        val unit = if (tenths == 10) "pawn" else "pawns"
        return "$whole.$fraction $unit"
    }
}
