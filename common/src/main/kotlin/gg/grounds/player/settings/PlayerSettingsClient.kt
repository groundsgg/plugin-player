package gg.grounds.player.settings

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

/**
 * The per-player settings the network stores: the language and default region (service-player), and
 * whether friend requests and private messages are accepted (service-social). Both services are
 * REST-only and accept any workload token with audience `grounds-services`, which the chart
 * projects to [tokenFile] (`groundsToken`).
 *
 * Blocking; callers keep it off the tick thread. Every failure is an exception, never a default:
 * showing "on" for a setting that could not be read would be a claim about the player.
 */
class PlayerSettingsClient(
    private val playerServiceUrl: String,
    private val socialServiceUrl: String,
    private val tokenFile: Path,
) {
    private val http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()

    /** The stored language tag, or null when the player follows their client's language. */
    fun locale(player: UUID): String? {
        val body = send(get("$playerServiceUrl/v1/players/$player/locale"))
        return body
            .get("locale")
            ?.takeUnless { it.isJsonNull }
            ?.asString
            ?.takeIf { it.isNotBlank() }
    }

    /** Stores [locale]; null clears it, so the client's own language applies again. */
    fun setLocale(player: UUID, locale: String?) {
        send(
            put(
                "$playerServiceUrl/v1/players/$player/locale",
                JsonObject().apply { addProperty("locale", locale) },
            )
        )
    }

    /** The stored region code (`eu`, `na`, ...), or null when the player takes the nearest. */
    fun region(player: UUID): String? {
        val body = send(get("$playerServiceUrl/v1/players/$player/region"))
        return body
            .get("region")
            ?.takeUnless { it.isJsonNull }
            ?.asString
            ?.takeIf { it.isNotBlank() }
    }

    /** Stores [region]; null clears it, so the geo-steered nearest region applies again. */
    fun setRegion(player: UUID, region: String?) {
        send(
            put(
                "$playerServiceUrl/v1/players/$player/region",
                JsonObject().apply { addProperty("region", region) },
            )
        )
    }

    fun friendRequestsEnabled(player: UUID): Boolean =
        send(get(socialSettingsUrl(player, "friend-requests"))).get("enabled").asBoolean

    fun setFriendRequestsEnabled(player: UUID, enabled: Boolean) {
        send(put(socialSettingsUrl(player, "friend-requests"), enabled(enabled)))
    }

    fun privateMessagesEnabled(player: UUID): Boolean =
        send(get(socialSettingsUrl(player, "private-messages"))).get("enabled").asBoolean

    fun setPrivateMessagesEnabled(player: UUID, enabled: Boolean) {
        send(put(socialSettingsUrl(player, "private-messages"), enabled(enabled)))
    }

    fun globalChatEnabled(player: UUID): Boolean =
        send(get(socialSettingsUrl(player, "global-chat"))).get("enabled").asBoolean

    fun setGlobalChatEnabled(player: UUID, enabled: Boolean) {
        send(put(socialSettingsUrl(player, "global-chat"), enabled(enabled)))
    }

    private fun socialSettingsUrl(player: UUID, setting: String) =
        "$socialServiceUrl/v1/social/players/$player/$setting/settings"

    private fun enabled(enabled: Boolean) = JsonObject().apply { addProperty("enabled", enabled) }

    private fun get(url: String) = request(url).GET().build()

    private fun put(url: String, body: JsonObject) =
        request(url)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

    // The token is projected and rotated by the kubelet, so it is read per request.
    private fun request(url: String) =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("Authorization", "Bearer ${Files.readString(tokenFile).trim()}")

    private fun send(request: HttpRequest): JsonObject {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "${request.method()} ${request.uri()} answered ${response.statusCode()}"
        }
        val body = response.body()
        return if (body.isBlank()) JsonObject() else JsonParser.parseString(body).asJsonObject
    }

    companion object {
        private val TIMEOUT = Duration.ofSeconds(3)

        fun fromEnvironment(env: Map<String, String> = System.getenv()) =
            PlayerSettingsClient(
                playerServiceUrl = env["PLAYER_SERVICE_URL"] ?: "http://service-player:9000",
                socialServiceUrl = env["SOCIAL_SERVICE_URL"] ?: "http://service-social:9000",
                tokenFile = Path.of(env["GROUNDS_TOKEN_FILE"] ?: "/var/run/secrets/grounds/token"),
            )
    }
}
