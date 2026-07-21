package gg.grounds

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.config.MessagesConfig
import gg.grounds.config.MessagesConfigLoader
import gg.grounds.link.ForgeLinkClient
import gg.grounds.link.LinkCommand
import gg.grounds.listener.PlayerConnectionListener
import gg.grounds.presence.PlayerHeartbeatScheduler
import gg.grounds.presence.PlayerPresenceService
import gg.grounds.presence.PlayerSessionQueryImpl
import gg.grounds.proxy.api.PlayerSessionQuery
import gg.grounds.proxy.api.ProxyServiceRegistry
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
    // plugin-proxy owns the ProxyServiceRegistry this plugin publishes its session lookup into.
    dependencies = [Dependency(id = "plugin-proxy")],
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
                proxy = proxy,
                plugin = this,
            ),
        )

        // Publish the network-wide player lookup. plugin-proxy's ProxyService falls back to this
        // for
        // anyone who is not on this proxy — it is what lets chat and social reach across proxies.
        ProxyServiceRegistry.register(
            PlayerSessionQuery::class.java,
            PlayerSessionQueryImpl(playerPresenceService),
        )

        registerLinkCommands(messages)

        heartbeatScheduler.start()
        logger.info("Configured player presence gRPC client (target={})", target)
    }

    /**
     * /link + /unlink talk to forge over HTTP, which needs the platform context forge injects into
     * pushed workloads. Outside that context (a bare local proxy, say) the env vars are absent —
     * skip the commands rather than register ones that can only ever fail.
     */
    private fun registerLinkCommands(messages: MessagesConfig) {
        val forgeUrl = System.getenv("GROUNDS_FORGE_URL")?.trim()?.trimEnd('/')
        if (forgeUrl.isNullOrEmpty()) {
            logger.info("GROUNDS_FORGE_URL is not set; /link is unavailable")
            return
        }

        // Read the token per call, not once here: it comes from a mounted Secret and holding it in
        // a field is how credentials end up in logs.
        val client =
            ForgeLinkClient(
                forgeUrl = forgeUrl,
                tokenProvider = {
                    System.getenv("GROUNDS_TOKEN")?.trim()?.takeIf { it.isNotEmpty() }
                },
            )

        proxy.commandManager.register(LinkCommand.create(client, messages, logger))
        proxy.commandManager.register(LinkCommand.createUnlink(client, messages, logger))
        logger.info("Registered /link and /unlink (forgeUrl={})", forgeUrl)
    }

    @Subscribe
    fun onShutdown(event: ProxyShutdownEvent) {
        ProxyServiceRegistry.unregister(PlayerSessionQuery::class.java)
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
