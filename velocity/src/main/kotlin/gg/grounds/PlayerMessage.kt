package gg.grounds

import gg.grounds.i18n.SystemCategory
import gg.grounds.i18n.SystemMessageKey

enum class PlayerMessage(override val id: String, override val category: SystemCategory) :
    SystemMessageKey {
    LOGIN_SERVICE_UNAVAILABLE("login.service-unavailable", SystemCategory.ERROR),
    LOGIN_ALREADY_ONLINE("login.already-online", SystemCategory.ERROR),
    LOGIN_INVALID_REQUEST("login.invalid-request", SystemCategory.ERROR),
    LOGIN_ERROR("login.error", SystemCategory.ERROR),
    LANG_USAGE("lang.usage", SystemCategory.WARNING),
    LANG_PLAYERS_ONLY("lang.players-only", SystemCategory.ERROR),
    LANG_UNKNOWN("lang.unknown", SystemCategory.ERROR),
    LANG_SET("lang.set", SystemCategory.SUCCESS),
    LINK_PLAYERS_ONLY("link.players-only", SystemCategory.ERROR),
    LINK_WORKING("link.working", SystemCategory.INFO),
    LINK_PROMPT("link.prompt", SystemCategory.INFO),
    LINK_CLICK("link.click", SystemCategory.INFO),
    LINK_FAILED("link.failed", SystemCategory.ERROR),
    UNLINK_DONE("unlink.done", SystemCategory.SUCCESS),
}
