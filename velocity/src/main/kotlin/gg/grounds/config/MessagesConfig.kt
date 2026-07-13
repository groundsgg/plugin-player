package gg.grounds.config

data class MessagesConfig(
    val serviceUnavailable: String = "Login service unavailable",
    val alreadyOnline: String = "You are already online.",
    val invalidRequest: String = "Invalid login request.",
    val genericError: String = "Unable to create player session.",
    // /link and /unlink. The consent itself happens in a browser, so the
    // prompt's job is to explain why a player is being sent out of the game.
    val linkPlayersOnly: String = "Only players can link a Microsoft account.",
    val linkWorking: String = "Preparing your Microsoft sign-in...",
    val linkPrompt: String = "Sign in with Microsoft to find friends who play here:",
    val linkClickHere: String = "[Click to connect]",
    val linkFailed: String = "Could not start the link right now. Please try again.",
    val unlinkDone: String = "Your Microsoft account has been disconnected.",
)
