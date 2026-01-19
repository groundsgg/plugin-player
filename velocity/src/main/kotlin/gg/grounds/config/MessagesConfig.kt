package gg.grounds.config

data class MessagesConfig(
    val serviceUnavailable: String = "Login service unavailable",
    val alreadyOnline: String = "You are already online.",
    val invalidRequest: String = "Invalid login request.",
    val genericError: String = "Unable to create player session.",
)
