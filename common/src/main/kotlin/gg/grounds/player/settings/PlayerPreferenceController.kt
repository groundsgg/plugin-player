package gg.grounds.player.settings

import java.util.UUID

/** Connection ownership and read generations are separate from UI/application revisions. */
class PlayerPreferenceController<P : Any, V>(
    private val idOf: (P) -> UUID,
    private val read: (UUID) -> V,
    private val dispatch: (Runnable) -> Unit,
    private val apply: (P, V) -> Unit,
    private val execute: (Runnable) -> Unit,
    /** Optional synchronous connection default; null skips initialization, not successful reads. */
    private val initial: V? = null,
) : AutoCloseable {
    private class Ticket

    private class Session<P>(val player: P) {
        var ticket = Ticket()
        var revision = 0L
    }

    private val lock = Any()
    private val sessions = HashMap<UUID, Session<P>>()
    private var active = true

    /** Called during initial configuration before any frame exists; the returned read may block. */
    fun connect(player: P): Runnable {
        val id = idOf(player)
        val session =
            synchronized(lock) {
                if (!active) return Runnable {}
                sessions[id]
                    ?.takeIf { it.player === player }
                    ?.let {
                        return request(id, it)
                    }
                Session(player).also { sessions[id] = it }
            }
        initial?.let { apply(player, it) }
        return synchronized(lock) { request(id, session) }
    }

    fun refresh(playerId: UUID) {
        val request =
            synchronized(lock) {
                if (!active) return
                sessions[playerId]?.let { request(playerId, it) }
            }
        request?.let(execute)
    }

    private fun request(id: UUID, session: Session<P>): Runnable {
        val ticket = Ticket().also { session.ticket = it }
        return Runnable {
            val allowed =
                synchronized(lock) {
                    active && sessions[id] === session && session.ticket === ticket
                }
            if (!allowed) return@Runnable
            val result = runCatching { read(id) }
            if (result.isFailure) return@Runnable
            val value = result.getOrThrow()
            dispatch(
                Runnable {
                    synchronized(lock) {
                        if (active && sessions[id] === session && session.ticket === ticket) {
                            session.revision++
                            apply(session.player, value)
                        }
                    }
                }
            )
        }
    }

    /** Apply only after HTTP committed; invalidate reads that started before that save. */
    fun saved(player: P, value: V) {
        val id = idOf(player)
        val (session, revision) =
            synchronized(lock) {
                if (!active) return
                val session = sessions[id]?.takeIf { it.player === player } ?: return
                session.ticket = Ticket()
                session.revision++
                session to session.revision
            }
        dispatch(
            Runnable {
                synchronized(lock) {
                    if (active && sessions[id] === session && session.revision == revision)
                        apply(player, value)
                }
            }
        )
    }

    /** A delayed PUT response carries no commit version: confirm against current storage. */
    fun savedAndRefresh(player: P, value: V) {
        saved(player, value)
        refresh(idOf(player))
    }

    fun reconcile() {
        val ids = synchronized(lock) { if (!active) return else sessions.keys.toList() }
        ids.forEach(::refresh)
    }

    fun disconnect(player: P) {
        synchronized(lock) {
            val id = idOf(player)
            if (sessions[id]?.player === player) sessions.remove(id)
        }
    }

    override fun close() {
        synchronized(lock) {
            active = false
            sessions.clear()
        }
    }
}
