package gg.grounds.presence

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import org.slf4j.Logger

class PlayerHeartbeatScheduler(
    private val plugin: Any,
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val presenceService: PlayerPresenceService,
) {
    private var heartbeatTask: ScheduledTask? = null

    fun start() {
        heartbeatTask?.cancel()
        heartbeatTask = null
        val intervalResolution =
            resolveHeartbeatIntervalSeconds(
                System.getenv(HEARTBEAT_INTERVAL_ENV),
                System.getenv(SESSION_TTL_ENV),
            )
        heartbeatTask =
            proxy.scheduler
                .buildTask(plugin, Runnable { sendHeartbeats() })
                .repeat(intervalResolution.effectiveIntervalSeconds, TimeUnit.SECONDS)
                .schedule()
        if (intervalResolution.wasClamped) {
            logger.warn(
                "Player heartbeat interval clamped (configuredIntervalSeconds={}, effectiveIntervalSeconds={}, sessionTtlSeconds={}, maxIntervalSeconds={})",
                intervalResolution.configuredIntervalSeconds,
                intervalResolution.effectiveIntervalSeconds,
                intervalResolution.sessionTtlSeconds,
                intervalResolution.maxIntervalSeconds,
            )
        }
        logger.info(
            "Configured player presence heartbeat task (intervalSeconds={}, sessionTtlSeconds={})",
            intervalResolution.effectiveIntervalSeconds,
            intervalResolution.sessionTtlSeconds,
        )
    }

    fun stop() {
        heartbeatTask?.cancel()
        heartbeatTask = null
    }

    private fun sendHeartbeats() {
        val playerIds = proxy.allPlayers.map { it.uniqueId }
        if (playerIds.isEmpty()) {
            return
        }

        val result = presenceService.heartbeatBatch(playerIds)
        if (!result.success) {
            logger.error(
                "Player session heartbeat batch failed (playerCount={}, updated={}, missing={}, reason={})",
                playerIds.size,
                result.updated,
                result.missing,
                result.message,
            )
        } else if (result.missing > 0) {
            logger.warn(
                "Player session heartbeat batch completed with missing sessions (playerCount={}, updated={}, missing={})",
                playerIds.size,
                result.updated,
                result.missing,
            )
        } else {
            logger.debug(
                "Player session heartbeat batch completed (playerCount={}, updated={}, missing={}, result=success)",
                playerIds.size,
                result.updated,
                result.missing,
            )
        }
    }

    internal data class HeartbeatIntervalResolution(
        val configuredIntervalSeconds: Long,
        val effectiveIntervalSeconds: Long,
        val sessionTtlSeconds: Long,
        val maxIntervalSeconds: Long,
        val wasClamped: Boolean,
    )

    companion object {
        private const val DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 30L
        private const val DEFAULT_SESSION_TTL_SECONDS = 90L
        private const val HEARTBEAT_INTERVAL_ENV = "PLAYER_PRESENCE_HEARTBEAT_SECONDS"
        private const val SESSION_TTL_ENV = "PLAYER_SESSIONS_TTL"

        internal fun resolveHeartbeatIntervalSeconds(
            heartbeatIntervalRaw: String?,
            sessionTtlRaw: String?,
        ): HeartbeatIntervalResolution {
            val configuredInterval =
                heartbeatIntervalRaw?.trim()?.toLongOrNull()?.takeIf { it > 0 }
                    ?: DEFAULT_HEARTBEAT_INTERVAL_SECONDS
            val sessionTtlSeconds =
                parseSessionTtlSeconds(sessionTtlRaw) ?: DEFAULT_SESSION_TTL_SECONDS
            val maxIntervalSeconds = max(1L, sessionTtlSeconds / 3)
            val effectiveInterval = min(configuredInterval, maxIntervalSeconds)
            return HeartbeatIntervalResolution(
                configuredIntervalSeconds = configuredInterval,
                effectiveIntervalSeconds = effectiveInterval,
                sessionTtlSeconds = sessionTtlSeconds,
                maxIntervalSeconds = maxIntervalSeconds,
                wasClamped = effectiveInterval != configuredInterval,
            )
        }

        private fun parseSessionTtlSeconds(raw: String?): Long? {
            val value = raw?.trim()?.lowercase() ?: return null
            if (value.isEmpty()) {
                return null
            }
            return when {
                value.endsWith("s") -> value.removeSuffix("s").toLongOrNull()
                value.endsWith("m") -> value.removeSuffix("m").toLongOrNull()?.times(60)
                value.endsWith("h") -> value.removeSuffix("h").toLongOrNull()?.times(3600)
                else -> null
            }?.takeIf { it > 0 }
        }
    }
}
