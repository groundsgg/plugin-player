package gg.grounds.player.settings

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlayerPreferenceControllerTest {
    private class Player(val id: UUID = UUID.randomUUID())

    @Test
    fun `delayed GUI completion cannot replace a newer committed network locale`() {
        for (networkApplied in listOf(false, true)) {
            val p = Player()
            val ticks = ArrayDeque<Runnable>()
            val applied = mutableListOf<String?>()
            var stored: String? = "en"
            val c =
                PlayerPreferenceController<Player, String?>(
                    { it.id },
                    { stored },
                    { ticks += it },
                    { _, v -> applied += v },
                    { it.run() },
                )
            c.connect(p).run()
            ticks.removeFirst().run()
            // GUI de committed earlier, but its response was delayed while /lang fr committed.
            stored = "fr"
            c.refresh(p.id)
            if (networkApplied) ticks.removeFirst().run()
            c.savedAndRefresh(p, "de")
            while (ticks.isNotEmpty()) ticks.removeFirst().run()
            assertEquals("fr", applied.last(), "Storage must win over a delayed PUT response")
            c.close()
        }
    }

    @Test
    fun `successful null follows client while a failed read keeps the previous locale`() {
        val p = Player()
        val applied = mutableListOf<String?>()
        var stored: String? = "de"
        var fail = false
        val c =
            PlayerPreferenceController<Player, String?>(
                { it.id },
                { if (fail) error("down") else stored },
                { it.run() },
                { _, value -> applied += value },
                { it.run() },
                initial = null,
            )
        c.connect(p).run()
        assertEquals(listOf("de"), applied)
        fail = true
        c.refresh(p.id)
        assertEquals(listOf("de"), applied)
        fail = false
        stored = null
        c.refresh(p.id)
        assertEquals(listOf("de", null), applied)
    }

    @Test
    fun `committed save rejects the initial lookup already queued for application`() {
        val p = Player()
        val applied = mutableListOf<String?>()
        val ticks = ArrayDeque<Runnable>()
        val c =
            PlayerPreferenceController<Player, String?>(
                { it.id },
                { "en" },
                { ticks += it },
                { _, v -> applied += v },
                { it.run() },
                initial = null,
            )
        c.connect(p).run()
        c.saved(p, "de")
        ticks.forEach { it.run() }
        assertEquals(listOf("de"), applied)
    }

    @Test
    fun `replacement connection ignores old reads saves and late disconnect`() {
        val old = Player()
        val current = Player(old.id)
        val ticks = ArrayDeque<Runnable>()
        val applied = mutableListOf<Pair<Player, String?>>()
        var stored: String? = "en"
        val c =
            PlayerPreferenceController<Player, String?>(
                { it.id },
                { stored },
                { ticks += it },
                { p, v -> applied += p to v },
                { it.run() },
                initial = null,
            )
        c.connect(old).run()
        stored = "fr"
        c.connect(current).run()
        c.saved(old, "de")
        c.disconnect(old)
        ticks.forEach { it.run() }
        assertEquals(listOf(current to "fr"), applied)
        stored = null
        c.refresh(current.id)
        ticks.last().run()
        assertEquals(current to null, applied.last())
    }

    @Test
    fun `UUID invalidations and reconciliation read only owned connections and close cancels queued work`() {
        val p = Player()
        val ticks = ArrayDeque<Runnable>()
        val applied = mutableListOf<String?>()
        val reads = mutableListOf<UUID>()
        var stored: String? = "en"
        val c =
            PlayerPreferenceController<Player, String?>(
                { it.id },
                {
                    reads += it
                    stored
                },
                { ticks += it },
                { _, v -> applied += v },
                { it.run() },
                initial = null,
            )
        c.connect(p).run()
        ticks.removeFirst().run()
        c.refresh(UUID.randomUUID())
        assertEquals(listOf(p.id), reads)
        stored = "es"
        c.reconcile()
        ticks.removeFirst().run()
        assertEquals(listOf("en", "es"), applied)
        c.saved(p, null)
        c.close()
        ticks.removeFirst().run()
        c.refresh(p.id)
        c.reconcile()
        c.saved(p, "de")
        c.connect(Player()).run()
        assertEquals(listOf("en", "es"), applied)
        assertEquals(listOf(p.id, p.id), reads)
    }
}
