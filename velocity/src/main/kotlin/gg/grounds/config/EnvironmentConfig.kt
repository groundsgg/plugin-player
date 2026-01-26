package gg.grounds.config

class EnvironmentConfig {
    fun presenceTarget(): String {
        return requireEnv("PLAYER_PRESENCE_GRPC_TARGET")
    }

    fun permissionsTarget(presenceTarget: String): String {
        return env("PERMISSIONS_GRPC_TARGET") ?: presenceTarget
    }

    fun permissionsEventsTarget(permissionsTarget: String): String {
        return env("PERMISSIONS_EVENTS_GRPC_TARGET") ?: permissionsTarget
    }

    fun permissionsEventsServerId(): String? {
        return env("PERMISSIONS_EVENTS_SERVER_ID")
    }

    fun permissionsRefreshIntervalSeconds(
        defaultSeconds: Long = DEFAULT_PERMISSIONS_REFRESH_SECONDS
    ): Long {
        val rawValue = env("PERMISSIONS_CACHE_REFRESH_SECONDS").orEmpty().trim()
        val parsed = rawValue.toLongOrNull()
        return when {
            parsed == null -> defaultSeconds
            parsed <= 0 -> 0L
            else -> parsed
        }
    }

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    private fun requireEnv(name: String): String {
        return env(name) ?: error("Missing required environment variable $name")
    }

    companion object {
        private const val DEFAULT_PERMISSIONS_REFRESH_SECONDS = 30L
    }
}
