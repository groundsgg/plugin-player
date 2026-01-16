package gg.grounds.player.presence

import java.time.Duration

data class PlayerPresenceClientConfig(
    val target: String,
    val plaintext: Boolean,
    val timeout: Duration,
) {
    init {
        require(target.isNotBlank()) { "target must not be blank" }
        require(!timeout.isZero && !timeout.isNegative) { "timeout must be > 0" }
    }

    companion object {
        fun defaults(): PlayerPresenceClientConfig =
            PlayerPresenceClientConfig(
                target = "localhost:9000",
                plaintext = true,
                timeout = Duration.ofSeconds(2),
            )
    }
}
