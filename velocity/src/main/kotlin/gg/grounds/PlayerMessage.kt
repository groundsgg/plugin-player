package gg.grounds

import gg.grounds.i18n.SystemCategory
import gg.grounds.i18n.SystemLevel
import gg.grounds.i18n.SystemMessageKey

enum class PlayerMessage(
    override val id: String,
    override val category: SystemCategory = SystemCategory.GENERAL,
    override val level: SystemLevel = SystemLevel.INFO,
) : SystemMessageKey {
    LOGIN_SERVICE_UNAVAILABLE("login.service-unavailable", level = SystemLevel.ERROR),
    LOGIN_ALREADY_ONLINE("login.already-online", level = SystemLevel.ERROR),
    LOGIN_INVALID_REQUEST("login.invalid-request", level = SystemLevel.ERROR),
    LOGIN_ERROR("login.error", level = SystemLevel.ERROR),
    LANG_USAGE("lang.usage", level = SystemLevel.WARNING),
    LANG_PLAYERS_ONLY("lang.players-only", level = SystemLevel.ERROR),
    LANG_UNKNOWN("lang.unknown", level = SystemLevel.ERROR),
    LANG_SET("lang.set", level = SystemLevel.SUCCESS),
    LINK_PLAYERS_ONLY("link.players-only", level = SystemLevel.ERROR),
    LINK_WORKING("link.working"),
    LINK_PROMPT("link.prompt"),
    LINK_CLICK("link.click"),
    LINK_FAILED("link.failed", level = SystemLevel.ERROR),
    UNLINK_DONE("unlink.done", level = SystemLevel.SUCCESS),
}
