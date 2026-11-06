package gg.grounds.player.settings

/** Canonical service-social audiences, shared by the portal and game settings. */
enum class PrivacyAudience(val wire: String, val label: SettingsMessage) {
    ANYONE("anyone", SettingsMessage.SETTINGS_AUDIENCE_ANYONE),
    FRIENDS("friends", SettingsMessage.SETTINGS_AUDIENCE_FRIENDS),
    NOBODY("nobody", SettingsMessage.SETTINGS_AUDIENCE_NOBODY);

    fun step(forward: Boolean): PrivacyAudience =
        entries[Math.floorMod(ordinal + if (forward) 1 else -1, entries.size)]

    companion object {
        fun fromWire(value: String): PrivacyAudience = entries.first { it.wire == value }
    }
}

data class PrivacySettings(
    val showStats: Boolean,
    val showOnline: Boolean,
    val partyInvites: PrivacyAudience,
    val privateMessages: PrivacyAudience,
)
