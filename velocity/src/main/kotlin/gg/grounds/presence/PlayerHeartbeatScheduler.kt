package gg.grounds.presence

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import java.util.concurrent.TimeUnit
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
        val heartbeatIntervalSeconds = resolveHeartbeatIntervalSeconds()
        heartbeatTask =
            proxy.scheduler
                .buildTask(plugin, Runnable { sendHeartbeats() })
                .repeat(heartbeatIntervalSeconds, TimeUnit.SECONDS)
                .schedule()
        logger.info(
            "Configured player presence heartbeat task (intervalSeconds={})",
            heartbeatIntervalSeconds,
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
                "Player session heartbeat batch failed (playerCount={}, error={})",
                playerIds.size,
                result.message,
            )
        } else {
            logger.debug(
                "Player session heartbeat batch completed (playerCount={}, result=success)",
                playerIds.size,
            )
        }
    }

    private fun resolveHeartbeatIntervalSeconds(): Long {
        val raw = System.getenv("PLAYER_PRESENCE_HEARTBEAT_SECONDS") ?: return 30
        return raw.toLongOrNull()?.takeIf { it > 0 } ?: 30
    }
}
