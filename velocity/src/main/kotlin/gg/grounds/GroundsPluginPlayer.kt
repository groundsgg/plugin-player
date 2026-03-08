package gg.grounds

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.config.MessagesConfigLoader
import gg.grounds.listener.PlayerConnectionListener
import gg.grounds.presence.PlayerHeartbeatScheduler
import gg.grounds.presence.PlayerPresenceService
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
    private val heartbeatScheduler =
        PlayerHeartbeatScheduler(this, proxy, logger, playerPresenceService)

    init {
        logger.info("Initialized plugin (plugin=plugin-player, version={})", BuildInfo.VERSION)
    }

    @Subscribe
    fun onInitialize(event: ProxyInitializeEvent) {
        registerProviders()

        val messages = MessagesConfigLoader(logger, dataDirectory).loadOrCreate()
        val target = resolveTarget()
        playerPresenceService.configure(target)

        proxy.eventManager.register(
            this,
            PlayerConnectionListener(
                logger = logger,
                playerPresenceService = playerPresenceService,
                messages = messages,
            ),
        )

        heartbeatScheduler.start()
        logger.info("Configured player presence gRPC client (target={})", target)
    }

    @Subscribe
    fun onShutdown(event: ProxyShutdownEvent) {
        heartbeatScheduler.stop()
        playerPresenceService.close()
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

    private fun resolveTarget(): String {
        return System.getenv("PLAYER_PRESENCE_GRPC_TARGET")?.takeIf { it.isNotBlank() }
            ?: error("Missing required environment variable PLAYER_PRESENCE_GRPC_TARGET")
    }
}
