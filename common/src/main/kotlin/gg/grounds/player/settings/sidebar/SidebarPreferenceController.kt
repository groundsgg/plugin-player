package gg.grounds.player.settings.sidebar

import gg.grounds.player.settings.PlayerPreferenceController
import java.util.UUID

/** Sidebar connections stay hidden until their stored visibility has loaded. */
class SidebarPreferenceController<P : Any>(
    idOf: (P) -> UUID,
    read: (UUID) -> Boolean,
    dispatch: (Runnable) -> Unit,
    apply: (P, Boolean) -> Unit,
    execute: (Runnable) -> Unit,
) : AutoCloseable {
    private val preference =
        PlayerPreferenceController(idOf, read, dispatch, apply, execute, initial = false)

    fun connect(player: P): Runnable = preference.connect(player)

    fun refresh(playerId: UUID) = preference.refresh(playerId)

    fun saved(player: P, enabled: Boolean) = preference.saved(player, enabled)

    fun reconcile() = preference.reconcile()

    fun disconnect(player: P) = preference.disconnect(player)

    override fun close() = preference.close()
}
