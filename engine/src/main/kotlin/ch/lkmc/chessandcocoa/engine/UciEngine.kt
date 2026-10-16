// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.engine

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A UCI client over one [UciTransport]. One search runs at a time; callers
 * serialize access. Every search re-sends its full configuration, so no
 * option set for one caller survives into another's search.
 *
 * ```
 *   caller ──analyze()──▶ UciEngine ──send()──▶ transport ──▶ engine
 *                            ▲                                  │
 *                            └──── reader thread ◀──readLine()──┘
 * ```
 */
class UciEngine(private val transport: UciTransport) : AutoCloseable {

    private val lines = LinkedBlockingQueue<String>()

    @Volatile
    private var closed = false

    /** Engine name from "id name", known after [start]. */
    var name: String = "unknown"
        private set

    private var applied: AppliedOptions? = null

    private val reader = Thread({ pump() }, "uci-reader").apply {
        isDaemon = true
        start()
    }

    /** Performs the UCI handshake. Must be called once before [analyze]. */
    fun start() {
        transport.send("uci")
        while (true) {
            val line = next() ?: throw EngineException("Engine exited during handshake")
            if (line.startsWith("id name ")) name = line.removePrefix("id name ").trim()
            if (line == "uciok") break
        }
        awaitReady()
    }

    /**
     * Runs one bounded search. [isCancelled] is polled while waiting; when it
     * turns true the search is stopped and the partial result is returned
     * with complete = false.
     */
    fun analyze(request: AnalysisRequest, isCancelled: () -> Boolean = { false }): AnalysisResult {
        configure(request)
        if (request.profile.clearBeforeSearch) {
            transport.send("ucinewgame")
            awaitReady()
        }

        transport.send(positionCommand(request))
        transport.send(goCommand(request))

        val collector = InfoCollector(request.multiPv)
        var stopped = false
        while (true) {
            if (!stopped && isCancelled()) {
                transport.send("stop")
                stopped = true
            }
            val line = poll() ?: if (closed) throw EngineException("Engine exited during search") else continue
            if (line.startsWith("info ")) collector.accept(line)
            if (line.startsWith("bestmove")) {
                val best = line.split(' ').getOrNull(1)?.takeUnless { it == "(none)" }
                return AnalysisResult(
                    request = request,
                    engineName = name,
                    lines = collector.lines(),
                    bestMove = best,
                    complete = !stopped,
                    nodes = collector.nodes,
                )
            }
        }
    }

    override fun close() {
        closed = true
        transport.close()
    }

    private fun configure(request: AnalysisRequest) {
        val wanted = AppliedOptions(request.profile.threads, request.profile.hashMb, request.multiPv)
        if (wanted == applied) return
        transport.send("setoption name Threads value ${wanted.threads}")
        transport.send("setoption name Hash value ${wanted.hashMb}")
        transport.send("setoption name MultiPV value ${wanted.multiPv}")
        transport.send("setoption name UCI_ShowWDL value true")
        awaitReady()
        applied = wanted
    }

    private fun awaitReady() {
        transport.send("isready")
        while (true) {
            val line = next() ?: throw EngineException("Engine exited before readyok")
            if (line == "readyok") return
        }
    }

    private fun next(): String? {
        while (true) {
            poll()?.let { return it }
            if (closed) return null
        }
    }

    private fun poll(): String? = lines.poll(POLL_MILLIS, TimeUnit.MILLISECONDS)

    private fun pump() {
        while (true) {
            val line = runCatching { transport.readLine() }.getOrNull()
            if (line == null) {
                closed = true
                return
            }
            lines.put(line)
        }
    }

    private data class AppliedOptions(val threads: Int, val hashMb: Int, val multiPv: Int)

    private companion object {
        const val POLL_MILLIS = 20L

        fun positionCommand(request: AnalysisRequest): String {
            val base = "position fen ${request.initialFen}"
            return if (request.moves.isEmpty()) base else "$base moves ${request.moves.joinToString(" ")}"
        }

        fun goCommand(request: AnalysisRequest): String = buildString {
            append("go")
            request.limit.nodes?.let { append(" nodes ").append(it) }
            request.limit.depth?.let { append(" depth ").append(it) }
            if (request.searchMoves.isNotEmpty()) append(" searchmoves ").append(request.searchMoves.joinToString(" "))
        }
    }
}

class EngineException(message: String) : RuntimeException(message)

/**
 * Keeps the latest complete "info … pv …" line per MultiPV slot. Lines
 * without a pv (currmove, hashfull reports) carry no candidate and are
 * only used for the node count.
 */
internal class InfoCollector(private val multiPv: Int) {

    private val bySlot = sortedMapOf<Int, CandidateLine>()

    var nodes: Long = 0
        private set

    fun accept(line: String) {
        val tokens = line.split(' ').filter { it.isNotEmpty() }
        val parsed = parseInfo(tokens) ?: return
        nodes = maxOf(nodes, parsed.nodes)
        if (parsed.multiPv <= multiPv) bySlot[parsed.multiPv] = parsed
    }

    fun lines(): List<CandidateLine> = bySlot.values.toList()

    private fun parseInfo(tokens: List<String>): CandidateLine? {
        var depth = 0
        var multi = 1
        var nodes = 0L
        var score: Score? = null
        var bound = ScoreBound.EXACT
        var wdl: Wdl? = null
        var pv: List<String> = emptyList()

        var i = 1
        while (i < tokens.size) {
            when (tokens[i]) {
                "depth" -> depth = tokens.getOrNull(++i)?.toIntOrNull() ?: 0
                "multipv" -> multi = tokens.getOrNull(++i)?.toIntOrNull() ?: 1
                "nodes" -> nodes = tokens.getOrNull(++i)?.toLongOrNull() ?: 0
                "score" -> {
                    val kind = tokens.getOrNull(++i)
                    val value = tokens.getOrNull(++i)?.toIntOrNull()
                    score = when {
                        value == null -> null
                        kind == "cp" -> Score.Centipawns(value)
                        kind == "mate" -> Score.Mate(value)
                        else -> null
                    }
                }
                "lowerbound" -> bound = ScoreBound.LOWER
                "upperbound" -> bound = ScoreBound.UPPER
                "wdl" -> {
                    val w = tokens.getOrNull(i + 1)?.toIntOrNull()
                    val d = tokens.getOrNull(i + 2)?.toIntOrNull()
                    val l = tokens.getOrNull(i + 3)?.toIntOrNull()
                    if (w != null && d != null && l != null) wdl = Wdl(w, d, l)
                    i += 3
                }
                "pv" -> {
                    pv = tokens.subList(i + 1, tokens.size)
                    i = tokens.size
                }
                // "string" carries free text to the end of the line.
                "string" -> return null
            }
            i++
        }

        val finalScore = score ?: return null
        if (pv.isEmpty()) {
            this.nodes = maxOf(this.nodes, nodes)
            return null
        }
        return CandidateLine(multi, pv.first(), finalScore, bound, depth, nodes, pv, wdl)
    }
}
