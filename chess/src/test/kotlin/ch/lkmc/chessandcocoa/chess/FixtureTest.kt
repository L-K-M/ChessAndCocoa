// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-checks every field of tools/chessfixtures/gen_fixtures.py output
 * (python-chess 1.11.2). Where python-chess and the KDoc differ the KDoc
 * wins; the differences are noted per field below:
 *
 *  - `fen` is python-chess `fen(en_passant="fen")`: the stored en-passant
 *    square is written even when no capture is legal, matching [fen].
 *  - `ep_legal` is python-chess `has_legal_en_passant()`, the same
 *    semantics [repetitionKey] applies to the en-passant field.
 *  - Game outcomes are computed by the generator under the KDoc rules
 *    (automatic threefold/fifty-move draws, mate first), because
 *    python-chess `outcome()` needs claim_draw and orders differently.
 */
class FixtureTest {

    @Serializable
    data class FixturePosition(
        val fen: String,
        val moves: List<String>,
        val sans: Map<String, String>,
        val check: Boolean,
        val checkmate: Boolean,
        val stalemate: Boolean,
        val insufficient: Boolean,
        val ep: String?,
        val ep_legal: Boolean,
        val halfmove: Int,
        val fullmove: Int,
        val material: Map<String, Int>,
        val pinned: Map<String, Map<String, String>>,
        val attackers: Map<String, Map<String, List<String>>>,
    )

    @Serializable
    data class FixtureOutcome(val winner: String?, val reason: String)

    @Serializable
    data class FixtureGame(
        val initial_fen: String,
        val moves: List<String>,
        val first_outcome_ply: Int?,
        val outcome: FixtureOutcome?,
    )

    @Serializable
    data class Fixtures(val positions: List<FixturePosition>, val games: List<FixtureGame>)

    private val fixtures: Fixtures by lazy {
        val text = requireNotNull(javaClass.getResource("/fixtures.json")) { "fixtures.json missing" }
            .readText()
        Json.decodeFromString(text)
    }

    private fun side(name: String): Side = if (name == "white") Side.WHITE else Side.BLACK

    @Test
    fun positionFields() {
        assertTrue(fixtures.positions.size > 500, "fixture file looks truncated")
        for (f in fixtures.positions) {
            val pos = Position.fromFen(f.fen)
            assertEquals(f.fen, pos.fen(), "fen round trip")
            assertEquals(f.moves, pos.legalMoves().map { it.uci }.sorted(), "legal moves ${f.fen}")
            assertEquals(f.check, pos.isCheck(), "isCheck ${f.fen}")
            assertEquals(f.checkmate, pos.isCheckmate(), "isCheckmate ${f.fen}")
            assertEquals(f.stalemate, pos.isStalemate(), "isStalemate ${f.fen}")
            assertEquals(f.insufficient, pos.hasInsufficientMaterial(), "insufficient ${f.fen}")
            assertEquals(f.ep, pos.enPassantSquare?.name, "ep square ${f.fen}")
            assertEquals(f.halfmove, pos.halfmoveClock, "halfmove ${f.fen}")
            assertEquals(f.fullmove, pos.fullmoveNumber, "fullmove ${f.fen}")
            assertEquals(f.material["white"], pos.material(Side.WHITE), "material ${f.fen}")
            assertEquals(f.material["black"], pos.material(Side.BLACK), "material ${f.fen}")

            // repetitionKey carries the ep square exactly when an ep
            // capture is legal, unlike fen(), which keeps the stored one.
            val fields = f.fen.split(' ')
            val expectedKey =
                "${fields[0]} ${fields[1]} ${fields[2]} ${if (f.ep_legal) fields[3] else "-"}"
            assertEquals(expectedKey, pos.repetitionKey(), "repetition key ${f.fen}")

            for ((sideName, expected) in f.pinned) {
                val actual = pos.pinnedPieces(side(sideName))
                    .mapKeys { it.key.name }
                    .mapValues { it.value.name }
                assertEquals(expected, actual, "pins ${f.fen} $sideName")
            }
            for ((sq, byColor) in f.attackers) {
                for ((sideName, expected) in byColor) {
                    val actual = pos.attackers(Square.parse(sq), side(sideName)).map { it.name }
                    assertEquals(expected, actual, "attackers ${f.fen} $sq $sideName")
                }
            }
        }
    }

    @Test
    fun sansAndRoundTrip() {
        for (f in fixtures.positions) {
            val pos = Position.fromFen(f.fen)
            for (move in pos.legalMoves()) {
                val expected = f.sans[move.uci]
                    ?: error("missing SAN for ${move.uci} in ${f.fen}")
                assertEquals(expected, pos.san(move), "SAN ${move.uci} in ${f.fen}")
                assertEquals(move, pos.parseSan(expected), "parseSan ${expected} in ${f.fen}")
            }
        }
    }

    @Test
    fun gameOutcomes() {
        for (g in fixtures.games) {
            var game = Game.new(g.initial_fen)
            assertEquals(null, game.outcome(), "ply 0 outcome of $g")
            g.moves.forEachIndexed { i, uci ->
                game = game.play(Move.parseUci(uci))
                val ply = i + 1
                if (g.first_outcome_ply != null && ply < g.first_outcome_ply) {
                    assertEquals(null, game.outcome(), "ply $ply of $g")
                }
            }
            if (g.first_outcome_ply != null) {
                game = game.truncate(g.first_outcome_ply)
                val outcome = assertNotNull(game.outcome(), "outcome of $g")
                assertEquals(g.outcome?.reason, outcome.reason.name, "reason of $g")
                assertEquals(g.outcome?.winner?.let(::side), outcome.winner, "winner of $g")
            } else {
                assertEquals(null, game.outcome(), "outcome of $g")
            }
        }
    }
}
