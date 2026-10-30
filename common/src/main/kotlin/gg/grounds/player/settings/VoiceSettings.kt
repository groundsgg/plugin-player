package gg.grounds.player.settings

import java.util.UUID

/**
 * A player's voice settings, stored by service-social: [partyVoice] puts them into their party's
 * voice group, [hints] shows the line above the hotbar when their voice changes.
 */
data class VoicePreferences(val partyVoice: Boolean, val hints: Boolean)

/** Where the player's voice is: no mod connected, or [group] (null for proximity voice). */
data class VoiceStatus(val connected: Boolean, val group: String?)

/**
 * The voice chat on this server, as /settings sees it. plugin-voice registers one; a server without
 * voice chat has none, and the menu says so.
 */
interface VoiceSettings {
    fun status(player: UUID): VoiceStatus

    /** [player] saved new [preferences] in /settings; apply them on this server right away. */
    fun preferencesChanged(player: UUID, preferences: VoicePreferences)
}
