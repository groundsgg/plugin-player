package gg.grounds

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.config.PluginConfigLoader
import gg.grounds.listener.PlayerConnectionListener
import gg.grounds.presence.PlayerPresenceService
import java.nio.file.Path
import org.slf4j.Logger

@Plugin(id = "grounds-plugin-player", name = "Grounds Player Plugin")
class GroundsPluginPlayer
@Inject
constructor(
    private val proxy: ProxyServer,
    private val logger: Logger,
    @param:DataDirectory private val dataDirectory: Path,
) {
    private val playerPresenceService = PlayerPresenceService(logger)

    init {
        logger.info("VelocityPlayerPlugin initialized")
    }

    @Subscribe
    fun onInitialize(@Suppress("UNUSED_PARAMETER") event: ProxyInitializeEvent) {
        val config = PluginConfigLoader(logger, dataDirectory).loadOrCreate()
        val clientConfig = config.playerPresence.toClientConfig()
        playerPresenceService.configure(clientConfig)

        proxy.eventManager.register(
            this,
            PlayerConnectionListener(
                logger = logger,
                playerPresenceService = playerPresenceService,
                messages = config.messages,
            ),
        )

        logger.info(
            "PlayerPresence gRPC configured (target={}, plaintext={}, timeoutMs={})",
            clientConfig.target,
            clientConfig.plaintext,
            clientConfig.timeout.toMillis(),
        )
    }

    @Subscribe
    fun onShutdown(@Suppress("UNUSED_PARAMETER") event: ProxyShutdownEvent) {
        playerPresenceService.close()
    }
}
