package gg.grounds.link

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import tools.jackson.databind.json.JsonMapper

/**
 * HTTP client for forge's Minecraft-link endpoints.
 *
 * Mirrors plugin-grounds-platform's WhitelistApiClient: the JDK's built-in HttpClient (already on
 * the classpath, nothing to shade) and the token pulled from the environment per request rather
 * than held in a field — a credential in a field ends up in some `toString()` sooner or later.
 *
 * Every call is async. A Velocity command runs on the netty thread, and forge has to talk to
 * Microsoft before it can answer, so blocking here would stall the whole proxy.
 */
class ForgeLinkClient(
    private val forgeUrl: String,
    private val tokenProvider: () -> String?,
    private val httpClient: HttpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) {

    /** forge could not be asked, or refused. The message is for the log, not for the player. */
    class ForgeLinkException(message: String) : RuntimeException(message)

    private val mapper = JsonMapper.builder().build()

    private fun request(path: String): HttpRequest.Builder {
        val token =
            tokenProvider()
                ?: throw ForgeLinkException("GROUNDS_TOKEN is not set; refusing to call forge")
        return HttpRequest.newBuilder(URI.create("$forgeUrl$path"))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(20))
    }

    /**
     * Ask forge for a Microsoft consent URL for this player.
     *
     * The UUID is only a claim at this point — forge verifies against the Minecraft profile behind
     * the completed consent and refuses a mismatch, so nothing here has to be trusted.
     */
    fun startLink(playerId: UUID): CompletableFuture<String> {
        val body = mapper.writeValueAsString(mapOf("minecraftUuid" to playerId.toString()))
        val req =
            request("/v1/minecraft/link/start")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply { res ->
            if (res.statusCode() / 100 != 2) {
                throw ForgeLinkException("link/start failed (status=${res.statusCode()})")
            }
            mapper.readTree(res.body())["authorizeUrl"]?.asString()
                ?: throw ForgeLinkException("link/start returned no authorizeUrl")
        }
    }

    /** Drop the stored Microsoft token. Forgetting an absent link is a success, not an error. */
    fun unlink(playerId: UUID): CompletableFuture<Void> {
        val req = request("/v1/minecraft/$playerId/link").DELETE().build()
        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding()).thenAccept { res ->
            if (res.statusCode() / 100 != 2) {
                throw ForgeLinkException("unlink failed (status=${res.statusCode()})")
            }
        }
    }
}
