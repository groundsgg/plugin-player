package gg.grounds.player.settings.sidebar

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SidebarPreferenceControllerTest {
    private class Player(val id: UUID = UUID.randomUUID())

    @Test
    fun `cold connections hide until loaded and failed refresh keeps the known setting`() {
        val player = Player()
        val applied = mutableListOf<Boolean>()
        var fail = false
        var stored = true
        val c =
            SidebarPreferenceController<Player>(
                { it.id },
                { if (fail) error("down") else stored },
                { it.run() },
                { _, v -> applied += v },
                { it.run() },
            )
        val read = c.connect(player)
        assertEquals(listOf(false), applied)
        read.run()
        assertEquals(true, applied.last())
        stored = false
        c.refresh(player.id)
        assertEquals(false, applied.last())
        fail = true
        c.refresh(player.id)
        assertEquals(false, applied.last())
        c.close()
    }

    @Test
    fun `reconnect and late old disconnect cannot overwrite or remove the new owner`() {
        val id = UUID.randomUUID()
        val old = Player(id)
        val current = Player(id)
        val applied = mutableListOf<Pair<Player, Boolean>>()
        val callbacks = ArrayDeque<Runnable>()
        var stored = true
        val c =
            SidebarPreferenceController<Player>(
                { it.id },
                { stored },
                { callbacks.add(it) },
                { p, v -> applied += p to v },
                { it.run() },
            )
        c.connect(old).run()
        stored = false
        c.connect(current).run()
        c.disconnect(old)
        callbacks.forEach { it.run() }
        assertFalse(applied.any { it.first === current && it.second })
        assertEquals(current to false, applied.last())
        stored = true
        c.refresh(id)
        callbacks.last().run()
        assertEquals(current to true, applied.last())
        c.close()
    }

    @Test
    fun `committed GUI choice invalidates an in flight old read`() {
        val p = Player()
        val applied = mutableListOf<Boolean>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val c =
            SidebarPreferenceController<Player>(
                { it.id },
                {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    true
                },
                { it.run() },
                { _, v -> applied += v },
                { it.run() },
            )
        val request = c.connect(p)
        val thread = Thread { request.run() }
        thread.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        try {
            c.saved(p, false)
        } finally {
            release.countDown()
            thread.join(5000)
        }
        assertEquals(false, applied.last())
        assertFalse(applied.contains(true))
        c.close()
    }

    @Test
    fun `late save after a backend or proxy move refreshes only the current connection`() {
        val id = UUID.randomUUID()
        val old = Player(id)
        val current = Player(id)
        var stored = true
        val applied = mutableListOf<Pair<Player, Boolean>>()
        val c =
            SidebarPreferenceController<Player>(
                { it.id },
                { stored },
                { it.run() },
                { p, v -> applied += p to v },
                { it.run() },
            )
        c.connect(old).run()
        c.connect(current).run()
        stored = false // Service commits the old GUI PUT after the new connection read ON.
        c.saved(old, false)
        c.refresh(id) // The service-origin UUID event reaches this backend.
        assertEquals(current to false, applied.last())
        c.close()
        val count = applied.size
        c.refresh(id)
        c.saved(current, true)
        c.reconcile()
        assertEquals(count, applied.size)
    }
}
