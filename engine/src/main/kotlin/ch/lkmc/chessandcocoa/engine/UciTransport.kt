// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.engine

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File

/**
 * A line-based UCI channel to one engine instance. Implementations hide how
 * the engine runs: in-process through JNI on Android, a child process on the
 * desktop. Only [UciEngine] talks to a transport.
 */
interface UciTransport : AutoCloseable {
    /** Sends one command line; the transport appends the newline. */
    fun send(line: String)

    /** Blocks for the next output line; null once the engine has exited. */
    fun readLine(): String?

    override fun close()
}

/** Runs a UCI engine executable as a child process (desktop tests and the research harness). */
class ProcessUciTransport(executable: File) : UciTransport {

    private val process: Process = ProcessBuilder(executable.absolutePath)
        .redirectErrorStream(true)
        .start()

    private val writer: BufferedWriter = process.outputStream.bufferedWriter()
    private val reader: BufferedReader = process.inputStream.bufferedReader()

    override fun send(line: String) {
        writer.write(line)
        writer.newLine()
        writer.flush()
    }

    override fun readLine(): String? = reader.readLine()

    override fun close() {
        runCatching { send("quit") }
        if (!process.waitFor(QUIT_GRACE_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
    }

    private companion object {
        const val QUIT_GRACE_SECONDS = 2L
    }
}
