package gg.grounds.permissions.commands

import com.google.protobuf.Timestamp
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ProxyServer
import java.time.Duration
import java.time.Instant
import java.util.UUID

object PermissionsCommandParser {
    fun resolvePlayerId(proxy: ProxyServer, source: CommandSource, raw: String): UUID? {
        val asUuid = runCatching { UUID.fromString(raw) }.getOrNull()
        if (asUuid != null) {
            return asUuid
        }
        return proxy.getPlayer(raw).map { it.uniqueId }.orElse(null)
    }

    fun parseExpiresAt(rawDuration: String?): Timestamp? {
        if (rawDuration == null) {
            return null
        }
        val duration = parseDuration(rawDuration) ?: return null
        val expiresAt = Instant.now().plus(duration)
        return Timestamp.newBuilder()
            .setSeconds(expiresAt.epochSecond)
            .setNanos(expiresAt.nano)
            .build()
    }

    private fun parseDuration(raw: String): Duration? {
        val trimmed = raw.trim().lowercase()
        if (trimmed.isEmpty()) {
            return null
        }
        val valuePart = trimmed.dropLast(1)
        val unit = trimmed.last()
        val amount = valuePart.toLongOrNull() ?: return null
        if (amount <= 0) {
            return null
        }
        return when (unit) {
            's' -> Duration.ofSeconds(amount)
            'm' -> Duration.ofMinutes(amount)
            'h' -> Duration.ofHours(amount)
            'd' -> Duration.ofDays(amount)
            'w' -> Duration.ofDays(amount * 7)
            else -> null
        }
    }
}
