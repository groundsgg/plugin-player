package gg.grounds.permissions

import com.velocitypowered.api.permission.PermissionFunction
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import gg.grounds.grpc.permissions.PlayerPermissions
import gg.grounds.permissions.services.PermissionsService
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.slf4j.Logger

data class CachedPermissions(
    val playerId: UUID,
    val groupMemberships: Set<GroupMembership>,
    val directPermissions: Set<PermissionGrant>,
    val effectivePermissions: Set<String>,
    val updatedAt: Instant,
)

data class GroupMembership(val groupName: String, val expiresAt: Instant?)

data class PermissionGrant(val permission: String, val expiresAt: Instant?)

class PermissionsCache(
    private val permissionsService: PermissionsService,
    private val logger: Logger,
) : AutoCloseable {
    private val cache = ConcurrentHashMap<UUID, CachedPermissions>()
    private var refreshTask: ScheduledTask? = null

    fun cachePlayer(playerId: UUID): Boolean {
        val permissions = permissionsService.fetchPlayerPermissions(playerId) ?: return false
        cache[playerId] = permissions.toCached()
        return true
    }

    fun refreshPlayer(playerId: UUID): Boolean {
        val permissions = permissionsService.fetchPlayerPermissions(playerId) ?: return false
        cache[playerId] = permissions.toCached()
        return true
    }

    fun removePlayer(playerId: UUID) {
        cache.remove(playerId)
    }

    fun get(playerId: UUID): CachedPermissions? = cache[playerId]

    fun checkCached(playerId: UUID, permission: String): Boolean? {
        val cached = cache[playerId] ?: return null
        return cached.effectivePermissions.contains(permission)
    }

    fun createPermissionFunction(playerId: UUID): PermissionFunction {
        return PermissionFunction { permission ->
            if (permission.isBlank()) {
                return@PermissionFunction Tristate.UNDEFINED
            }
            val cached = cache[playerId] ?: return@PermissionFunction Tristate.UNDEFINED
            if (cached.effectivePermissions.contains(permission)) Tristate.TRUE
            else Tristate.UNDEFINED
        }
    }

    fun startAutoRefresh(proxy: ProxyServer, plugin: Any, refreshIntervalSeconds: Long) {
        if (refreshIntervalSeconds <= 0) {
            logger.info(
                "Permissions cache refresh disabled (intervalSeconds={})",
                refreshIntervalSeconds,
            )
            return
        }
        refreshTask?.cancel()
        refreshTask =
            proxy.scheduler
                .buildTask(plugin, Runnable { refreshOnlinePlayers(proxy) })
                .repeat(refreshIntervalSeconds, TimeUnit.SECONDS)
                .schedule()
        logger.info(
            "Permissions cache refresh scheduled (intervalSeconds={})",
            refreshIntervalSeconds,
        )
    }

    fun refreshOnlinePlayers(proxy: ProxyServer) {
        val players = proxy.allPlayers
        if (players.isEmpty()) {
            return
        }
        players.forEach { player ->
            if (!refreshPlayer(player.uniqueId)) {
                logger.warn(
                    "Failed to refresh cached permissions (playerId={}, username={})",
                    player.uniqueId,
                    player.username,
                )
            }
        }
    }

    override fun close() {
        refreshTask?.cancel()
        refreshTask = null
        cache.clear()
    }

    private fun PlayerPermissions.toCached(): CachedPermissions {
        return CachedPermissions(
            playerId = UUID.fromString(playerId),
            groupMemberships =
                groupMembershipsList
                    .map {
                        GroupMembership(
                            it.groupName,
                            if (it.hasExpiresAt()) {
                                Instant.ofEpochSecond(
                                    it.expiresAt.seconds,
                                    it.expiresAt.nanos.toLong(),
                                )
                            } else {
                                null
                            },
                        )
                    }
                    .toSet(),
            directPermissions =
                directPermissionGrantsList
                    .map {
                        PermissionGrant(
                            it.permission,
                            if (it.hasExpiresAt()) {
                                Instant.ofEpochSecond(
                                    it.expiresAt.seconds,
                                    it.expiresAt.nanos.toLong(),
                                )
                            } else {
                                null
                            },
                        )
                    }
                    .toSet(),
            effectivePermissions = effectivePermissionsList.toSet(),
            updatedAt = Instant.now(),
        )
    }
}
