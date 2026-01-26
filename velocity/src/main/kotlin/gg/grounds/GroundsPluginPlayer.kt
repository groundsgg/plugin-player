package gg.grounds

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.config.EnvironmentConfig
import gg.grounds.config.MessagesConfigLoader
import gg.grounds.permissions.PermissionsCache
import gg.grounds.permissions.commands.PermissionsCommand
import gg.grounds.permissions.listener.PermissionsConnectionListener
import gg.grounds.permissions.listener.PermissionsSetupListener
import gg.grounds.permissions.services.PermissionsAdminService
import gg.grounds.permissions.services.PermissionsEventsSubscriber
import gg.grounds.permissions.services.PermissionsService
import gg.grounds.presence.PlayerPresenceService
import gg.grounds.presence.listener.PlayerConnectionListener
import io.grpc.LoadBalancerRegistry
import io.grpc.NameResolverRegistry
import io.grpc.internal.DnsNameResolverProvider
import io.grpc.internal.PickFirstLoadBalancerProvider
import java.nio.file.Path
import org.slf4j.Logger

@Plugin(
    id = "plugin-player",
    name = "Grounds Player Plugin",
    version = BuildInfo.VERSION,
    description = "A plugin which manages player related actions and data transactions",
    authors = ["Grounds Development Team and contributors"],
    url = "https://github.com/groundsgg/plugin-player",
)
class GroundsPluginPlayer
@Inject
constructor(
    private val proxy: ProxyServer,
    private val logger: Logger,
    @param:DataDirectory private val dataDirectory: Path,
) {
    private val playerPresenceService = PlayerPresenceService()
    private val permissionsService = PermissionsService(logger)
    private val permissionsCache = PermissionsCache(permissionsService, logger)
    private val permissionsAdminService = PermissionsAdminService(logger)
    private val permissionsEventsSubscriber = PermissionsEventsSubscriber(logger, permissionsCache)
    private val environmentConfig = EnvironmentConfig()

    init {
        logger.info("Initialized plugin (plugin=plugin-player, version={})", BuildInfo.VERSION)
    }

    @Subscribe
    fun onInitialize(event: ProxyInitializeEvent) {
        registerProviders()

        val messages = MessagesConfigLoader(logger, dataDirectory).loadOrCreate()
        val presenceTarget = environmentConfig.presenceTarget()
        val permissionsTarget = environmentConfig.permissionsTarget(presenceTarget)
        val permissionsEventsTarget = environmentConfig.permissionsEventsTarget(permissionsTarget)
        playerPresenceService.configure(presenceTarget)
        permissionsService.configure(permissionsTarget)
        permissionsAdminService.configure(permissionsTarget)
        permissionsCache.startAutoRefresh(
            proxy,
            this,
            environmentConfig.permissionsRefreshIntervalSeconds(),
        )
        permissionsEventsSubscriber.configure(
            permissionsEventsTarget,
            environmentConfig.permissionsEventsServerId(),
        )

        proxy.eventManager.register(
            this,
            PlayerConnectionListener(
                logger = logger,
                playerPresenceService = playerPresenceService,
                messages = messages,
            ),
        )
        proxy.eventManager.register(
            this,
            PermissionsConnectionListener(logger = logger, permissionsCache = permissionsCache),
        )
        proxy.eventManager.register(
            this,
            PermissionsSetupListener(permissionsCache = permissionsCache),
        )
        val permissionsCommand =
            PermissionsCommand(proxy, permissionsCache, permissionsService, permissionsAdminService)
                .create()
        proxy.commandManager.register(
            proxy.commandManager.metaBuilder(permissionsCommand).build(),
            permissionsCommand,
        )

        logger.info("Configured player presence gRPC client (target={})", presenceTarget)
        logger.info("Configured permissions gRPC client (target={})", permissionsTarget)
        logger.info(
            "Configured permissions events gRPC client (target={})",
            permissionsEventsTarget,
        )
    }

    @Subscribe
    fun onShutdown(event: ProxyShutdownEvent) {
        playerPresenceService.close()
        permissionsCache.close()
        permissionsService.close()
        permissionsAdminService.close()
        permissionsEventsSubscriber.close()
    }

    /**
     * Registers gRPC name resolver and load balancer providers so client channels can resolve DNS
     * targets and select endpoints when running inside Velocity's shaded environment. This manual
     * step avoids startup IllegalArgumentExceptions caused by shaded classes not being discoverable
     * via the default provider lookup.
     */
    private fun registerProviders() {
        NameResolverRegistry.getDefaultRegistry().register(DnsNameResolverProvider())
        LoadBalancerRegistry.getDefaultRegistry().register(PickFirstLoadBalancerProvider())
    }
}
