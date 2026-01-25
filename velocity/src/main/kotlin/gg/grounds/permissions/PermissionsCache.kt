package gg.grounds.permissions

import com.velocitypowered.api.permission.PermissionFunction
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import gg.grounds.grpc.permissions.EffectivePermissionDelta
import gg.grounds.grpc.permissions.GroupMembershipDelta
import gg.grounds.grpc.permissions.PermissionDelta
import gg.grounds.grpc.permissions.PermissionsChangeEvent
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

    fun applyChangeEvent(event: PermissionsChangeEvent): Boolean {
        val playerId =
            try {
                UUID.fromString(event.playerId)
            } catch (error: IllegalArgumentException) {
                return false
            }
        return applyDeltas(
            playerId,
            event.directPermissionDeltasList,
            event.groupMembershipDeltasList,
            event.effectivePermissionDeltasList,
        )
    }

    fun applyDeltas(
        playerId: UUID,
        directDeltas: List<PermissionDelta>,
        groupDeltas: List<GroupMembershipDelta>,
        effectiveDeltas: List<EffectivePermissionDelta>,
    ): Boolean {
        return cache.compute(playerId) { _, cached ->
            if (cached == null) {
                return@compute null
            }

            val directPermissions =
                cached.directPermissions.associateBy { it.permission }.toMutableMap()
            directDeltas.forEach { delta ->
                when (delta.action) {
                    PermissionDelta.Action.ADD -> {
                        directPermissions[delta.permission] =
                            PermissionGrant(
                                delta.permission,
                                delta.expiresAt
                                    .takeIf { it.seconds != 0L || it.nanos != 0 }
                                    ?.let { Instant.ofEpochSecond(it.seconds, it.nanos.toLong()) },
                            )
                    }
                    PermissionDelta.Action.REMOVE -> directPermissions.remove(delta.permission)
                    PermissionDelta.Action.ACTION_UNSPECIFIED -> Unit
                    PermissionDelta.Action.UNRECOGNIZED -> Unit
                }
            }

            val groupMemberships =
                cached.groupMemberships.associateBy { it.groupName }.toMutableMap()
            groupDeltas.forEach { delta ->
                when (delta.action) {
                    GroupMembershipDelta.Action.ADD -> {
                        groupMemberships[delta.groupName] =
                            GroupMembership(
                                delta.groupName,
                                delta.expiresAt
                                    .takeIf { it.seconds != 0L || it.nanos != 0 }
                                    ?.let { Instant.ofEpochSecond(it.seconds, it.nanos.toLong()) },
                            )
                    }
                    GroupMembershipDelta.Action.REMOVE -> groupMemberships.remove(delta.groupName)
                    GroupMembershipDelta.Action.ACTION_UNSPECIFIED -> Unit
                    GroupMembershipDelta.Action.UNRECOGNIZED -> Unit
                }
            }

            val effectivePermissions = cached.effectivePermissions.toMutableSet()
            effectiveDeltas.forEach { delta ->
                when (delta.action) {
                    EffectivePermissionDelta.Action.ADD ->
                        effectivePermissions.add(delta.permission)
                    EffectivePermissionDelta.Action.REMOVE ->
                        effectivePermissions.remove(delta.permission)
                    EffectivePermissionDelta.Action.ACTION_UNSPECIFIED -> Unit
                    EffectivePermissionDelta.Action.UNRECOGNIZED -> Unit
                }
            }

            cached.copy(
                groupMemberships = groupMemberships.values.toSet(),
                directPermissions = directPermissions.values.toSet(),
                effectivePermissions = effectivePermissions,
                updatedAt = Instant.now(),
            )
        } != null
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
