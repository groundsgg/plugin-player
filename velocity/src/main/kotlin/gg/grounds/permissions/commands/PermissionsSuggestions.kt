package gg.grounds.permissions.commands

import com.mojang.brigadier.suggestion.SuggestionProvider
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.grpc.permissions.ListGroupsRequest
import gg.grounds.permissions.services.PermissionsAdminService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class PermissionsSuggestions(
    private val proxy: ProxyServer,
    permissionsAdminService: PermissionsAdminService,
) {
    private val groupNameCache = GroupNameCache(permissionsAdminService)

    fun player(): SuggestionProvider<CommandSource> = SuggestionProvider { _, builder ->
        proxy.allPlayers.forEach { player -> builder.suggest(player.username) }
        builder.buildFuture()
    }

    fun group(): SuggestionProvider<CommandSource> = SuggestionProvider { _, builder ->
        val prefix = builder.remaining
        groupNameCache.suggest(prefix).forEach { builder.suggest(it) }
        builder.buildFuture()
    }

    fun permission(): SuggestionProvider<CommandSource> = SuggestionProvider { _, builder ->
        PERMISSION_SAMPLES.filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .forEach { builder.suggest(it) }
        builder.buildFuture()
    }

    fun duration(): SuggestionProvider<CommandSource> = SuggestionProvider { _, builder ->
        DURATION_SAMPLES.filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .forEach { builder.suggest(it) }
        builder.buildFuture()
    }

    private class GroupNameCache(private val permissionsAdminService: PermissionsAdminService) {
        private val names = AtomicReference<List<String>>(emptyList())
        private val lastRefreshMillis = AtomicLong(0)
        private val inFlight = AtomicBoolean(false)

        fun suggest(prefix: String): List<String> {
            refreshIfNeeded()
            return names.get().filter { it.startsWith(prefix, ignoreCase = true) }
        }

        private fun refreshIfNeeded() {
            val now = System.currentTimeMillis()
            if (now - lastRefreshMillis.get() < CACHE_TTL_MILLIS) {
                return
            }
            if (!inFlight.compareAndSet(false, true)) {
                return
            }
            CompletableFuture.runAsync {
                try {
                    val reply =
                        permissionsAdminService.listGroups(
                            ListGroupsRequest.newBuilder().setIncludePermissions(false).build()
                        )
                    val updated = reply?.groupsList?.map { it.groupName } ?: names.get()
                    names.set(updated)
                } finally {
                    lastRefreshMillis.set(now)
                    inFlight.set(false)
                }
            }
        }

        companion object {
            private const val CACHE_TTL_MILLIS = 30000L
        }
    }

    companion object {
        private val PERMISSION_SAMPLES = listOf("grounds.permissions.admin")
        private val DURATION_SAMPLES = listOf("30m", "1h", "12h", "1d", "7d")
    }
}
