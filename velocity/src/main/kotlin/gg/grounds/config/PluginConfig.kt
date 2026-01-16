package gg.grounds.config

import gg.grounds.player.presence.PlayerPresenceClientConfig
import java.time.Duration

data class PluginConfig(
    val playerPresence: PlayerPresence = PlayerPresence(),
    val messages: Messages = Messages(),
) {
    data class PlayerPresence(
        val target: String = PlayerPresenceClientConfig.defaults().target,
        val plaintext: Boolean = PlayerPresenceClientConfig.defaults().plaintext,
        val timeoutMillis: Long = PlayerPresenceClientConfig.defaults().timeout.toMillis(),
    ) {
        fun toClientConfig(): PlayerPresenceClientConfig {
            val millis = maxOf(1L, timeoutMillis)
            return PlayerPresenceClientConfig(
                target = target,
                plaintext = plaintext,
                timeout = Duration.ofMillis(millis),
            )
        }
    }

    data class Messages(
        val serviceUnavailable: String = "Login service unavailable",
        val alreadyOnline: String = "You are already online.",
        val invalidRequest: String = "Invalid login request.",
        val genericError: String = "Unable to create player session.",
    )
}
