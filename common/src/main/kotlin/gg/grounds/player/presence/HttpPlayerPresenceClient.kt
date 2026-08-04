package gg.grounds.player.presence

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * service-player's HTTP API, as this proxy uses it.
 *
 * Nothing here throws. Every lookup runs on the command path — `/msg`, tab-complete, a join — and
 * an exception on Velocity's event loop is worse than not knowing the answer, so a failure resolves
 * to the method's documented "unknown" (null, an empty list, false). Login is the exception that
 * proves it: it reports *why* it failed, because "the player may not join" and "I could not ask"
 * lead the caller to different behaviour.
 *
 * Calls are synchronous with a short timeout. The presence service is one hop away in the same
 * cluster, and the alternative — letting a join wait on an unbounded call — is what the timeout
 * exists to prevent.
 */
class HttpPlayerPresenceClient(
    baseUrl: String,
    private val tokenProvider: () -> String? = WorkloadToken::load,
    private val httpClient: HttpClient =
        HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
) : AutoCloseable {

    private val baseUrl = normalizeBaseUrl(baseUrl)
    private val mapper = JsonMapper.builder().build()

    /**
     * Claims the network-wide session for a player.
     *
     * 409 is the network refusing a second login, which is an answer rather than a failure. A
     * timeout or 5xx is [PlayerLoginResult.Unavailable]: the proxy has learned nothing, and letting
     * the player in anyway is a decision for the caller to make knowingly.
     */
    fun tryLogin(
        playerId: UUID,
        playerName: String = "",
        proxyId: String = "",
        region: String = "",
    ): PlayerLoginResult {
        val body =
            mapper.writeValueAsString(
                mapOf(
                    "playerId" to playerId.toString(),
                    "playerName" to playerName,
                    "proxyId" to proxyId,
                    "region" to region,
                )
            )
        val response =
            send(
                request("/v1/players/sessions")
                    .header("Content-Type", APPLICATION_JSON)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
            ) ?: return PlayerLoginResult.Unavailable("presence service did not answer")

        return when (response.statusCode()) {
            201 -> PlayerLoginResult.Accepted
            409 -> PlayerLoginResult.AlreadyOnline
            400 -> PlayerLoginResult.Invalid(problemDetail(response.body()))
            in 500..599 -> PlayerLoginResult.Unavailable(problemDetail(response.body()))
            else -> PlayerLoginResult.Error("unexpected status ${response.statusCode()}")
        }
    }

    /**
     * Releases this proxy's session for a player.
     *
     * [proxyId] scopes the delete to the session this proxy owns: a logout that raced a
     * proxy-to-proxy transfer must not remove the session the next proxy just created. Blank omits
     * the scope, which deletes unconditionally.
     */
    fun logout(playerId: UUID, proxyId: String = ""): PlayerLogoutResult {
        val query = if (proxyId.isBlank()) "" else "?proxyId=${encode(proxyId)}"
        val response =
            send(request("/v1/players/$playerId/session$query").DELETE())
                ?: return PlayerLogoutResult.Failed("presence service did not answer")

        return when (response.statusCode()) {
            204 -> PlayerLogoutResult.Removed
            404 -> PlayerLogoutResult.NotFound
            else -> PlayerLogoutResult.Failed("unexpected status ${response.statusCode()}")
        }
    }

    /** Keeps every session this proxy holds alive, in one call. */
    fun heartbeatBatch(playerIds: Collection<UUID>): PlayerHeartbeatResult {
        val body = mapper.writeValueAsString(mapOf("playerIds" to playerIds.map(UUID::toString)))
        val response =
            send(
                request("/v1/players/sessions/heartbeats")
                    .header("Content-Type", APPLICATION_JSON)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
            )
                ?: return PlayerHeartbeatResult(
                    success = false,
                    message = "presence service did not answer",
                    updated = 0,
                    missing = playerIds.size,
                )

        if (response.statusCode() != 200) {
            return PlayerHeartbeatResult(
                success = false,
                message = problemDetail(response.body()),
                updated = 0,
                missing = playerIds.size,
            )
        }
        val json = parse(response.body())
        return PlayerHeartbeatResult(
            success = true,
            message = "heartbeat accepted",
            updated = json?.get("updated")?.asInt() ?: 0,
            missing = json?.get("missing")?.asInt() ?: 0,
        )
    }

    /** Who and where a player is, or null when they are not online or we could not ask. */
    fun getSession(playerId: UUID): PlayerSessionInfo? =
        readSession("/v1/players/$playerId/session")

    /** The session behind a name, matched case-insensitively. */
    fun resolveName(playerName: String): PlayerSessionInfo? =
        readSession("/v1/players/sessions?name=${encode(playerName)}")

    /** Tab-complete candidates. The server caps the count; a blank prefix returns nothing. */
    fun suggestNames(prefix: String, limit: Int): List<String> {
        val response =
            send(
                request("/v1/players/names/suggestions?prefix=${encode(prefix)}&limit=$limit").GET()
            ) ?: return emptyList()
        if (response.statusCode() != 200) return emptyList()
        val names = parse(response.body())?.get("playerNames") ?: return emptyList()
        return names.mapNotNull { it.asString() }
    }

    /** Records the backend server a player moved to. False means the move was not recorded. */
    fun updateServer(playerId: UUID, serverName: String): Boolean {
        val body = mapper.writeValueAsString(mapOf("serverName" to serverName))
        val response =
            send(
                request("/v1/players/$playerId/session/server")
                    .header("Content-Type", APPLICATION_JSON)
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
            ) ?: return false
        return response.statusCode() == 204
    }

    /**
     * Players per backend server, network-wide. Null means the count is unknown — which is
     * deliberately distinct from a count of zero, because "nobody is online" is a number callers
     * will render.
     */
    fun countPlayersByServer(): ServerPlayerCounts? {
        val json = readJson("/v1/players/counts/servers") ?: return null
        val servers =
            json.get("servers")?.mapNotNull { entry ->
                val name = entry.get("serverName")?.asString() ?: return@mapNotNull null
                ServerPlayerCount(name, entry.get("players")?.asInt() ?: 0)
            } ?: emptyList()
        return ServerPlayerCounts(servers, json.get("total")?.asInt() ?: 0)
    }

    /** Players per proxy and region, network-wide. Null means unknown, as above. */
    fun countPlayersByProxy(): ProxyPlayerCounts? {
        val json = readJson("/v1/players/counts/proxies") ?: return null
        val proxies =
            json.get("proxies")?.mapNotNull { entry ->
                val id = entry.get("proxyId")?.asString() ?: return@mapNotNull null
                ProxyPlayerCount(
                    proxyId = id,
                    region = entry.get("region")?.asString()?.takeIf(String::isNotEmpty),
                    players = entry.get("players")?.asInt() ?: 0,
                )
            } ?: emptyList()
        return ProxyPlayerCounts(proxies, json.get("total")?.asInt() ?: 0)
    }

    /** The player's stored language tag, or null when they have chosen none. */
    fun getLocale(playerId: UUID): String? =
        readJson("/v1/players/$playerId/locale")
            ?.get("locale")
            ?.asString()
            ?.takeIf(String::isNotEmpty)

    /** Stores, or with a blank tag clears, the player's language. */
    fun setLocale(playerId: UUID, locale: String): Boolean {
        val body = mapper.writeValueAsString(mapOf("locale" to locale))
        val response =
            send(
                request("/v1/players/$playerId/locale")
                    .header("Content-Type", APPLICATION_JSON)
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
            ) ?: return false
        return response.statusCode() == 204
    }

    override fun close() {
        httpClient.close()
    }

    private fun readSession(path: String): PlayerSessionInfo? {
        val json = readJson(path) ?: return null
        val playerId =
            json.get("playerId")?.asString()?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            } ?: return null
        return PlayerSessionInfo(
            playerId = playerId,
            playerName = json.get("playerName")?.asString()?.takeIf(String::isNotEmpty),
            proxyId = json.get("proxyId")?.asString()?.takeIf(String::isNotEmpty),
            serverName = json.get("serverName")?.asString()?.takeIf(String::isNotEmpty),
            region = json.get("region")?.asString()?.takeIf(String::isNotEmpty),
            connectedAtMillis = parseInstantMillis(json.get("connectedAt")?.asString()),
        )
    }

    private fun readJson(path: String): JsonNode? {
        val response = send(request(path).GET()) ?: return null
        if (response.statusCode() != 200) return null
        return parse(response.body())
    }

    private fun request(path: String): HttpRequest.Builder {
        val builder =
            HttpRequest.newBuilder(URI.create("$baseUrl$path"))
                .header("Accept", "$APPLICATION_JSON, $PROBLEM_JSON")
                .timeout(REQUEST_TIMEOUT)
        tokenProvider()?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

    /** Null for anything that stopped the exchange: a timeout, a refused connection, a thread. */
    private fun send(builder: HttpRequest.Builder): HttpResponse<String>? =
        try {
            httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (_: Exception) {
            null
        }

    private fun parse(body: String?): JsonNode? =
        try {
            body?.takeIf(String::isNotBlank)?.let(mapper::readTree)
        } catch (_: Exception) {
            null
        }

    /** The `detail` out of an RFC 9457 body, falling back to something a log can still use. */
    private fun problemDetail(body: String?): String =
        parse(body)?.get("detail")?.asString()?.takeIf(String::isNotBlank)
            ?: "presence service rejected the request"

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun parseInstantMillis(value: String?): Long =
        value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    companion object {
        private const val APPLICATION_JSON = "application/json"
        private const val PROBLEM_JSON = "application/problem+json"
        private val CONNECT_TIMEOUT = Duration.ofSeconds(2)

        /**
         * Matches the deadline the gRPC client used, for the same reason: this is on the join path.
         */
        private val REQUEST_TIMEOUT = Duration.ofSeconds(2)

        /**
         * The deploy sets the service address without a scheme, the way `MATCH_SERVICE_URL` is set.
         * Prepending it here keeps that from being a silent `URI.create` failure at the first
         * login.
         */
        internal fun normalizeBaseUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
            else "http://$trimmed"
        }
    }
}
