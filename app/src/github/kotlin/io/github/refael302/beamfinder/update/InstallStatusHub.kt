package io.github.refael302.beamfinder.update

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/** Bridges [InstallStatusReceiver] callbacks to the suspending installer. */
internal object InstallStatusHub {
    data class Status(val code: Int, val message: String?)

    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Status>>()

    fun register(sessionId: Int): CompletableDeferred<Status> {
        val deferred = CompletableDeferred<Status>()
        pending[sessionId]?.cancel()
        pending[sessionId] = deferred
        return deferred
    }

    fun complete(sessionId: Int, status: Status) {
        pending.remove(sessionId)?.complete(status)
    }

    fun abandon(sessionId: Int) {
        pending.remove(sessionId)?.cancel()
    }
}
