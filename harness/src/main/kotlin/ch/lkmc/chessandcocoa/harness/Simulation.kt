// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.harness

import ch.lkmc.chessandcocoa.chess.Move
import ch.lkmc.chessandcocoa.chess.Side
import ch.lkmc.chessandcocoa.coach.analysis.EngineAnalyst
import ch.lkmc.chessandcocoa.coach.analysis.ScoreMath
import ch.lkmc.chessandcocoa.coach.analysis.Searcher
import ch.lkmc.chessandcocoa.coach.decide.Difficulty
import ch.lkmc.chessandcocoa.coach.game.Coach
import ch.lkmc.chessandcocoa.coach.game.GameRecord
import ch.lkmc.chessandcocoa.coach.teach.CommentaryLevel
import ch.lkmc.chessandcocoa.coach.teach.ExperiencePreferences
import ch.lkmc.chessandcocoa.coach.teach.TeachingEvent
import ch.lkmc.chessandcocoa.engine.ProcessUciTransport
import ch.lkmc.chessandcocoa.engine.UciEngine
import java.io.File
import kotlin.math.exp
import kotlin.random.Random

/** One engine process with its own searcher; a worker plays one game at a time. */
class Worker(stockfish: File) : AutoCloseable {
    private val engine = UciEngine(ProcessUciTransport(stockfish)).apply { start() }
    val searcher = Searcher(EngineAnalyst(engine))
    val coach = Coach(searcher)
    val proxy = LearnerProxy(searcher)

    override fun close() = engine.close()
}

/**
 * A weak human stand-in (concept review E3 asks for Maia-2; this is the
 * second proxy it names, a weakened Stockfish): a shallow MultiPV search,
 * softmax over scores, and an occasional random move. Not a model of people.
 */
class LearnerProxy(private val searcher: Searcher) {

    suspend fun choose(record: GameRecord, random: Random): Move {
        val position = record.game.current
        val legal = position.legalMoves()
        if (random.nextDouble() < RANDOM_MOVE_CHANCE) return legal[random.nextInt(legal.size)]

        val search = searcher.search(record.game, PROXY_NODES, multiPv = minOf(PROXY_LINES, legal.size))
        val side = position.sideToMove
        val scores = search.result.lines.map { line ->
            val cp = ScoreMath.clampedCp(search.score(line, side))
            line.move to cp
        }
        val best = scores.maxOf { it.second }
        val weights = scores.map { (_, cp) -> exp((cp - best) / TEMPERATURE_CP) }
        var pick = random.nextDouble() * weights.sum()
        scores.zip(weights).forEach { (entry, weight) ->
            pick -= weight
            if (pick <= 0) return Move.parseUci(entry.first)
        }
        return Move.parseUci(scores.first().first)
    }

    private companion object {
        const val RANDOM_MOVE_CHANCE = 0.08
        const val PROXY_NODES = 4_000L
        const val PROXY_LINES = 6
        const val TEMPERATURE_CP = 120.0
    }
}

/** Wall-clock cost of each pipeline step, desktop only. */
data class StepTiming(val ply: Int, val kind: StepKind, val millis: Long, val searches: Int, val nodes: Long)

enum class StepKind { DECISION, ASSESSMENT }

data class SimulatedGame(val record: GameRecord, val timings: List<StepTiming>, val events: List<TeachingEvent>)

/** Plays the app's opponent (through the real Coach API) against the learner proxy. */
suspend fun simulate(worker: Worker, difficulty: Difficulty, seed: Long, maxPlies: Int): SimulatedGame {
    val random = Random(seed)
    val humanSide = if (seed % 2 == 0L) Side.WHITE else Side.BLACK
    val preferences = ExperiencePreferences(commentary = CommentaryLevel.NORMAL)
    val coach = worker.coach
    val searcher = worker.searcher

    var record = coach.newGame("sim-$seed", humanSide, difficulty, seed, 0)
    val timings = mutableListOf<StepTiming>()
    val events = mutableListOf<TeachingEvent>()

    while (record.outcome == null && record.ply < maxPlies) {
        if (!record.isHumanTurn) {
            val (step, timing) = timed(searcher, record.ply, StepKind.DECISION) { coach.playComputerMove(record, preferences, 0) }
            record = step.record
            step.event?.let(events::add)
            timings += timing
            continue
        }
        // As in the app, the player's position is analysed while they think; only the rest is timed.
        coach.prepareHumanTurn(record)
        val move = worker.proxy.choose(record, random)
        val (step, timing) = timed(searcher, record.ply, StepKind.ASSESSMENT) { coach.playHumanMove(record, move, preferences, 0) }
        record = step.record
        step.event?.let(events::add)
        timings += timing
    }
    return SimulatedGame(record, timings, events)
}

private suspend fun <T> timed(searcher: Searcher, ply: Int, kind: StepKind, block: suspend () -> T): Pair<T, StepTiming> {
    val searches = searcher.searchCount
    val nodes = searcher.nodeCount
    val start = System.nanoTime()
    val result = block()
    val millis = (System.nanoTime() - start) / NANOS_PER_MILLI
    return result to StepTiming(ply, kind, millis, searcher.searchCount - searches, searcher.nodeCount - nodes)
}

private const val NANOS_PER_MILLI = 1_000_000L
