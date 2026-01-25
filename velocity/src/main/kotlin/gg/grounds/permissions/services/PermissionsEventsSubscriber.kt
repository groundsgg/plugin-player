package gg.grounds.permissions.services

import gg.grounds.grpc.permissions.PermissionsChangeEvent
import gg.grounds.grpc.permissions.SubscribePermissionsChangesRequest
import gg.grounds.permissions.GrpcPermissionsEventsClient
import gg.grounds.permissions.PermissionsCache
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.Logger

class PermissionsEventsSubscriber(
    private val logger: Logger,
    private val permissionsCache: PermissionsCache,
) : AutoCloseable {
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "permissions-events-subscriber").apply { isDaemon = true }
        }
    private val closed = AtomicBoolean(false)
    private val reconnectDelayMs = AtomicLong(MIN_RECONNECT_DELAY_MS)
    private var lastEventId: String? = null
    private var serverId: String? = null
    private lateinit var client: GrpcPermissionsEventsClient

    fun configure(target: String, serverId: String?) {
        closed.set(false)
        if (this::client.isInitialized) {
            client.close()
        }
        this.serverId = serverId
        client = GrpcPermissionsEventsClient.create(target)
        startStream()
    }

    private fun startStream() {
        if (closed.get()) {
            return
        }
        val requestBuilder = SubscribePermissionsChangesRequest.newBuilder()
        serverId?.let { requestBuilder.serverId = it }
        lastEventId?.let { requestBuilder.lastEventId = it }
        val request = requestBuilder.build()
        client.subscribe(request, StreamObserverImpl())
        logger.info(
            "Permissions event stream connected (serverId={}, lastEventId={})",
            serverId,
            lastEventId ?: "none",
        )
    }

    private fun scheduleReconnect(reason: String) {
        if (closed.get()) {
            return
        }
        val delayMs = reconnectDelayMs.get()
        logger.warn(
            "Permissions event stream disconnected (reason={}, retryInMs={})",
            reason,
            delayMs,
        )
        executor.schedule({ startStream() }, delayMs, TimeUnit.MILLISECONDS)
        val nextDelay = (delayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        reconnectDelayMs.set(nextDelay)
    }

    private fun resetBackoff() {
        reconnectDelayMs.set(MIN_RECONNECT_DELAY_MS)
    }

    override fun close() {
        closed.set(true)
        if (this::client.isInitialized) {
            client.close()
        }
        executor.shutdownNow()
    }

    private inner class StreamObserverImpl : StreamObserver<PermissionsChangeEvent> {
        override fun onNext(event: PermissionsChangeEvent) {
            resetBackoff()
            lastEventId = event.eventId
            val playerId =
                try {
                    UUID.fromString(event.playerId)
                } catch (error: IllegalArgumentException) {
                    logger.warn(
                        "Permissions change event rejected (eventId={}, reason=invalid_player_id)",
                        event.eventId,
                    )
                    return
                }
            if (permissionsCache.get(playerId) == null) {
                logger.debug(
                    "Permissions change event skipped (playerId={}, eventId={}, reason=player_not_cached)",
                    playerId,
                    event.eventId,
                )
                return
            }
            if (event.requiresFullRefresh) {
                val refreshed = permissionsCache.refreshPlayer(playerId)
                if (!refreshed) {
                    logger.warn(
                        "Permissions refresh failed (playerId={}, eventId={}, reason=refresh_failed)",
                        playerId,
                        event.eventId,
                    )
                    return
                }
                logger.debug(
                    "Permissions refresh applied (playerId={}, eventId={}, reason={})",
                    playerId,
                    event.eventId,
                    event.reason,
                )
                return
            }
            val applied = permissionsCache.applyChangeEvent(event)
            if (!applied) {
                logger.warn(
                    "Permissions change event rejected (playerId={}, eventId={}, reason=apply_failed)",
                    playerId,
                    event.eventId,
                )
                return
            }
            logger.debug(
                "Permissions change event applied (playerId={}, eventId={}, reason={})",
                playerId,
                event.eventId,
                event.reason,
            )
        }

        override fun onError(error: Throwable) {
            val reason =
                if (error is StatusRuntimeException) {
                    val description = error.status.description ?: "none"
                    "${error.status.code.name}:${description}"
                } else {
                    error.message ?: error::class.java.name
                }
            scheduleReconnect(reason)
        }

        override fun onCompleted() {
            scheduleReconnect("completed")
        }
    }

    companion object {
        private const val MIN_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
    }
}
