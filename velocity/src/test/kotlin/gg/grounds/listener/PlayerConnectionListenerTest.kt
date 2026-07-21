package gg.grounds.listener

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerConnectionListenerTest {
    private fun pendingSet(vararg ids: UUID): MutableSet<UUID> =
        ConcurrentHashMap.newKeySet<UUID>().apply { addAll(ids) }

    @Test
    fun releasesTheSessionWhenTheConnectionNeverBecameAPlayer() {
        val playerId = UUID.randomUUID()
        val pending = pendingSet(playerId)

        assertTrue(
            PlayerConnectionListener.shouldReleaseAbandonedLogin(pending, playerId, false),
            "a pre-login session with no player behind it must be released",
        )
        assertFalse(pending.contains(playerId))
    }

    @Test
    fun keepsTheSessionOfAPlayerWhoActuallyJoined() {
        val playerId = UUID.randomUUID()
        val pending = pendingSet(playerId)

        assertFalse(
            PlayerConnectionListener.shouldReleaseAbandonedLogin(pending, playerId, true),
            "the player is online - releasing would log out a live session",
        )
    }

    @Test
    fun neverReleasesASessionThisProxyDidNotCreate() {
        // The duplicate-login case: a second connection is denied at pre-login and never becomes
        // pending. Releasing on its behalf would log out the player who is legitimately online.
        val playerId = UUID.randomUUID()

        assertFalse(
            PlayerConnectionListener.shouldReleaseAbandonedLogin(pendingSet(), playerId, false)
        )
    }

    @Test
    fun releasesOnlyOnce() {
        val playerId = UUID.randomUUID()
        val pending = pendingSet(playerId)

        assertTrue(PlayerConnectionListener.shouldReleaseAbandonedLogin(pending, playerId, false))
        assertFalse(
            PlayerConnectionListener.shouldReleaseAbandonedLogin(pending, playerId, false),
            "a second run must not log out a player who reconnected in the meantime",
        )
    }
}
