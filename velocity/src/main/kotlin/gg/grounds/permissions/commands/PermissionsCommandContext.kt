package gg.grounds.permissions.commands

import com.google.protobuf.Timestamp
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.permissions.CachedPermissions
import gg.grounds.permissions.PermissionsCache
import gg.grounds.permissions.services.PermissionsAdminService
import gg.grounds.permissions.services.PermissionsService
import java.util.UUID
import net.kyori.adventure.text.Component

class PermissionsCommandContext(
    val proxy: ProxyServer,
    val permissionsCache: PermissionsCache,
    val permissionsService: PermissionsService,
    val permissionsAdminService: PermissionsAdminService,
) {
    fun resolvePlayerId(source: CommandSource, raw: String): UUID? {
        return PermissionsCommandParser.resolvePlayerId(proxy, source, raw)
    }

    fun refreshOnlinePlayers() {
        permissionsCache.refreshOnlinePlayers(proxy)
    }

    fun refreshPlayer(playerId: UUID): Boolean {
        return permissionsCache.refreshPlayer(playerId)
    }

    fun getOrRefreshCached(source: CommandSource, playerId: UUID): CachedPermissions? {
        permissionsCache.get(playerId)?.let {
            return it
        }
        if (!permissionsCache.refreshPlayer(playerId)) {
            source.sendMessage(Component.text("Failed to load permissions."))
            return null
        }
        return permissionsCache.get(playerId)
    }

    fun parseExpiryOrReport(source: CommandSource, rawDuration: String?): Timestamp? {
        if (rawDuration == null) {
            return null
        }
        val expiresAt = PermissionsCommandParser.parseExpiresAt(rawDuration)
        if (expiresAt == null) {
            source.sendMessage(Component.text("Invalid duration format."))
        }
        return expiresAt
    }
}
