package gg.grounds.presence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerHeartbeatSchedulerTest {
    @Test
    fun resolveHeartbeatIntervalUsesDefaultsWhenUnset() {
        val result = PlayerHeartbeatScheduler.resolveHeartbeatIntervalSeconds(null, null)

        assertEquals(30, result.configuredIntervalSeconds)
        assertEquals(30, result.effectiveIntervalSeconds)
        assertEquals(90, result.sessionTtlSeconds)
        assertEquals(30, result.maxIntervalSeconds)
        assertFalse(result.wasClamped)
    }

    @Test
    fun resolveHeartbeatIntervalClampsWhenAboveSafeMaximum() {
        val result = PlayerHeartbeatScheduler.resolveHeartbeatIntervalSeconds("60", "90s")

        assertEquals(60, result.configuredIntervalSeconds)
        assertEquals(30, result.effectiveIntervalSeconds)
        assertEquals(90, result.sessionTtlSeconds)
        assertEquals(30, result.maxIntervalSeconds)
        assertTrue(result.wasClamped)
    }

    @Test
    fun resolveHeartbeatIntervalDoesNotClampWhenWithinSafeMaximum() {
        val result = PlayerHeartbeatScheduler.resolveHeartbeatIntervalSeconds("20", "90s")

        assertEquals(20, result.configuredIntervalSeconds)
        assertEquals(20, result.effectiveIntervalSeconds)
        assertEquals(90, result.sessionTtlSeconds)
        assertEquals(30, result.maxIntervalSeconds)
        assertFalse(result.wasClamped)
    }

    @Test
    fun resolveHeartbeatIntervalParsesMinuteTtl() {
        val result = PlayerHeartbeatScheduler.resolveHeartbeatIntervalSeconds("90", "6m")

        assertEquals(90, result.configuredIntervalSeconds)
        assertEquals(90, result.effectiveIntervalSeconds)
        assertEquals(360, result.sessionTtlSeconds)
        assertEquals(120, result.maxIntervalSeconds)
        assertFalse(result.wasClamped)
    }

    @Test
    fun resolveHeartbeatIntervalFallsBackForInvalidValues() {
        val result = PlayerHeartbeatScheduler.resolveHeartbeatIntervalSeconds("-5", "oops")

        assertEquals(30, result.configuredIntervalSeconds)
        assertEquals(30, result.effectiveIntervalSeconds)
        assertEquals(90, result.sessionTtlSeconds)
        assertEquals(30, result.maxIntervalSeconds)
        assertFalse(result.wasClamped)
    }
}
