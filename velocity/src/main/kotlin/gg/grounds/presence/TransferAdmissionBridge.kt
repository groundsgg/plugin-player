package gg.grounds.presence

import com.velocitypowered.api.proxy.Player
import java.util.UUID

/** Optional runtime bridge; enabling the gate requires the patched runtime, never a fallback. */
object TransferAdmissionBridge {
    val required: Boolean
        get() = System.getenv("GROUNDS_TRANSFER_ADMISSION_ENABLED") == "true"

    data class Admission(val token: String, val connectionId: UUID)

    private fun runtime() = Class.forName("gg.grounds.runtime.transfer.TransferAdmission")

    fun admission(player: Player): Admission? {
        if (!required) return null
        val raw = runtime().getMethod("admission", Player::class.java).invoke(null, player)
        return parse(raw as? Map<*, *>, player.uniqueId, System.getenv("PROXY_ID"))
    }

    internal fun parse(raw: Map<*, *>?, profile: UUID, proxy: String?): Admission? {
        if (
            raw?.get("playerId") != profile.toString() ||
                raw["destinationProxyId"] != proxy ||
                proxy.isNullOrBlank()
        )
            return null
        val token =
            (raw["handoffToken"] as? String)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{43}")) }
                ?: return null
        val connection =
            runCatching { UUID.fromString(raw["connectionId"] as? String) }.getOrNull()
                ?: return null
        return Admission(token, connection)
    }

    fun complete(player: Player) {
        if (required) runtime().getMethod("complete", Player::class.java).invoke(null, player)
    }
}
