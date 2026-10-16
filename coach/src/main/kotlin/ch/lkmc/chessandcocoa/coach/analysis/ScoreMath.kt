// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.analysis

import ch.lkmc.chessandcocoa.engine.Score

/**
 * Comparisons between scores of one side. Mate and centipawn scores are
 * compared by kind, never subtracted (concept Section 10.1).
 */
object ScoreMath {

    /** A mate this many moves longer than the best one still counts as equivalent. */
    private const val MATE_SLACK_MOVES = 2

    /**
     * Whether [candidate] is acceptable against [reference] with a
     * centipawn [windowCp]. Missing a winning mate or walking into a mate the
     * reference avoids is never within a window.
     */
    fun withinWindow(candidate: Score, reference: Score, windowCp: Int): Boolean {
        if (candidate.rank() >= reference.rank()) return true

        return when {
            reference is Score.Mate && reference.moves > 0 ->
                candidate is Score.Mate && candidate.moves > 0 && candidate.moves <= reference.moves + MATE_SLACK_MOVES
            reference is Score.Mate -> true // every move loses; anything at least as long is covered above
            candidate is Score.Mate -> false // reference is centipawns, candidate gets mated
            else -> (reference as Score.Centipawns).cp - (candidate as Score.Centipawns).cp <= windowCp
        }
    }

    /**
     * Centipawn loss of [played] against [best], or null when either is a
     * mate score and the difference has no centipawn meaning.
     */
    fun lossCp(best: Score, played: Score): Int? {
        if (best !is Score.Centipawns || played !is Score.Centipawns) return null
        return (best.cp - played.cp).coerceAtLeast(0)
    }

    fun isWinningMate(score: Score): Boolean = score is Score.Mate && score.moves > 0

    fun isLosingMate(score: Score): Boolean = score is Score.Mate && score.moves <= 0

    /** Centipawns, with mates clamped to a large magnitude; for sorting and display bands only. */
    fun clampedCp(score: Score): Int = when (score) {
        is Score.Centipawns -> score.cp
        is Score.Mate -> if (score.moves > 0) MATE_CP else -MATE_CP
    }

    private const val MATE_CP = 10_000
}
