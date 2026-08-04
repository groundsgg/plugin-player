package gg.grounds.player.presence

import java.nio.file.Files
import java.nio.file.Path

/**
 * The projected ServiceAccount token this proxy presents to service-player.
 *
 * The Grounds charts project a short-lived token (audience `grounds-services`) into the proxy pod
 * and point [TOKEN_FILE_ENV] at it. kubelet rotates the file, so it is read per request rather than
 * held in a field — a cached token expires mid-shift, and a credential in a field ends up in some
 * `toString()` sooner or later.
 *
 * With no token file present — local dev against a service running `grounds.auth.enabled=false` —
 * this returns null and the request goes out unauthenticated.
 */
object WorkloadToken {
    const val TOKEN_FILE_ENV: String = "GROUNDS_TOKEN_FILE"
    const val DEFAULT_TOKEN_PATH: String = "/var/run/secrets/grounds/token"

    fun load(): String? = loadFrom(System.getenv(TOKEN_FILE_ENV) ?: DEFAULT_TOKEN_PATH)

    internal fun loadFrom(path: String): String? =
        try {
            val file = Path.of(path)
            if (Files.exists(file)) Files.readString(file).trim().ifEmpty { null } else null
        } catch (_: Exception) {
            null
        }
}
