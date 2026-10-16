// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.nativeengine

import ch.lkmc.chessandcocoa.coach.analysis.Analyst
import ch.lkmc.chessandcocoa.coach.analysis.EngineAnalyst
import ch.lkmc.chessandcocoa.engine.AnalysisRequest
import ch.lkmc.chessandcocoa.engine.AnalysisResult
import ch.lkmc.chessandcocoa.engine.UciEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class EngineStatus { STARTING, READY, FAILED }

/**
 * Owns the process's single in-process Stockfish (the native bridge allows
 * one per process lifetime) and exposes it as an [Analyst]. Searches issued
 * before the engine is ready simply wait for it.
 */
class EngineHost(scope: CoroutineScope) {

    private val ready = CompletableDeferred<Analyst>()
    private val _status = MutableStateFlow(EngineStatus.STARTING)

    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    /** The analyst callers use; it waits for the engine to finish starting. */
    val analyst: Analyst = object : Analyst {
        override suspend fun analyze(request: AnalysisRequest): AnalysisResult = ready.await().analyze(request)
        override val searchCount: Int get() = if (ready.isCompleted) ready.getCompleted().searchCount else 0
        override val nodeCount: Long get() = if (ready.isCompleted) ready.getCompleted().nodeCount else 0
    }

    init {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val engine = UciEngine(NativeStockfishTransport())
                engine.start()
                EngineAnalyst(engine)
            }.onSuccess { analyst ->
                ready.complete(analyst)
                _status.value = EngineStatus.READY
            }.onFailure { failure ->
                ready.completeExceptionally(failure)
                _status.value = EngineStatus.FAILED
            }
        }
    }
}
