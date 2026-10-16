// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.coach.analysis

import ch.lkmc.chessandcocoa.engine.AnalysisRequest
import ch.lkmc.chessandcocoa.engine.AnalysisResult
import ch.lkmc.chessandcocoa.engine.UciEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * The coach's only door to engine analysis. Callers describe a search; how
 * and where it runs is hidden. Results are deterministic for a given request
 * (single thread, node limit, cleared hash), which is what makes caching and
 * the policy-identity check of concept Section 16.3 sound.
 */
interface Analyst {
    /** Runs (or recalls) one search. Throws [CancellationException] when the caller is cancelled. */
    suspend fun analyze(request: AnalysisRequest): AnalysisResult

    /** Searches performed since creation, cache hits excluded. */
    val searchCount: Int

    /** Nodes searched since creation, cache hits excluded. */
    val nodeCount: Long
}

/**
 * [Analyst] over one [UciEngine]. Searches are serialized: one engine
 * instance runs one search at a time. A bounded LRU cache keyed by the full
 * request (history included, Section 11.2) avoids repeating searches that the
 * plan probe and live play share.
 */
class EngineAnalyst(
    private val engine: UciEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cacheSize: Int = DEFAULT_CACHE_SIZE,
) : Analyst {

    private val mutex = Mutex()

    private val cache = object : LinkedHashMap<AnalysisRequest, AnalysisResult>(cacheSize, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AnalysisRequest, AnalysisResult>): Boolean =
            size > cacheSize
    }

    @Volatile
    override var searchCount: Int = 0
        private set

    @Volatile
    override var nodeCount: Long = 0
        private set

    override suspend fun analyze(request: AnalysisRequest): AnalysisResult = mutex.withLock {
        cache[request]?.let { return@withLock it }

        val job = currentCoroutineContext().job
        val result = withContext(dispatcher) { engine.analyze(request) { !job.isActive } }
        currentCoroutineContext().ensureActive()

        // A stopped search is never cached or returned as analysis.
        if (!result.complete) throw CancellationException("Search stopped before completion")

        searchCount++
        nodeCount += result.nodes
        cache[request] = result
        result
    }

    private companion object {
        const val DEFAULT_CACHE_SIZE = 512
        const val LOAD_FACTOR = 0.75f
    }
}
