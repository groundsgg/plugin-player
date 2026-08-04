package gg.grounds.player.presence

import java.util.UUID

/**
 * A player's live session, as service-player knows it.
 *
 * Everything but the id may be absent: a session created by an older proxy carries no name, a
 * player who has not reached a backend yet is on no server, and a proxy that declares no region
 * leaves that unknown. Absent is a normal answer here rather than a fault, so these are nullable
 * rather than empty strings.
 */
data class PlayerSessionInfo(
    val playerId: UUID,
    val playerName: String?,
    val proxyId: String?,
    val serverName: String?,
    val region: String?,
    val connectedAtMillis: Long,
)

/**
 * What came of claiming a session.
 *
 * [AlreadyOnline] is a normal answer, not a failure — it is how the network refuses a second login.
 * [Unavailable] and [Error] are: the proxy could not ask, so it knows nothing about whether the
 * player may join.
 */
sealed class PlayerLoginResult {
    data object Accepted : PlayerLoginResult()

    data object AlreadyOnline : PlayerLoginResult()

    /** The service rejected the request itself. A bug on this side, not a transient condition. */
    data class Invalid(val message: String) : PlayerLoginResult()

    data class Unavailable(val message: String) : PlayerLoginResult()

    data class Error(val message: String) : PlayerLoginResult()
}

/** What came of releasing a session. Only [Removed] means this proxy actually held one. */
sealed class PlayerLogoutResult {
    data object Removed : PlayerLogoutResult()

    /** Already gone, expired, or taken over by another proxy. Nothing to do about any of them. */
    data object NotFound : PlayerLogoutResult()

    data class Failed(val message: String) : PlayerLogoutResult()
}

/**
 * What a heartbeat batch did. [missing] counts players with no session, which is normal in small
 * numbers — someone logged out between building the batch and the write landing.
 */
data class PlayerHeartbeatResult(
    val success: Boolean,
    val message: String,
    val updated: Int,
    val missing: Int,
)

data class ServerPlayerCount(val serverName: String, val players: Int)

/**
 * Players per backend server, network-wide. Only occupied servers appear; [total] can exceed their
 * sum because it counts players who have not reached a backend yet.
 */
data class ServerPlayerCounts(val servers: List<ServerPlayerCount>, val total: Int)

data class ProxyPlayerCount(val proxyId: String, val region: String?, val players: Int)

/** Players per proxy, network-wide. Only occupied proxies appear; [total] is their sum. */
data class ProxyPlayerCounts(val proxies: List<ProxyPlayerCount>, val total: Int)
