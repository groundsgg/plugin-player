package gg.grounds.config

/** Optional literal operator overrides; null uses the shared locale-aware source bundle. */
data class MessagesConfig(
    val serviceUnavailable: String? = null,
    val alreadyOnline: String? = null,
    val invalidRequest: String? = null,
    val genericError: String? = null,
)
