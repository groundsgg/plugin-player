package gg.grounds.player.settings

import gg.grounds.i18n.Translations
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SettingsMenuTest {

    private val messages =
        Translations.forBundle(
            "gg.grounds.player.settings.messages",
            SettingsMenu::class.java.classLoader,
        )

    /** The menu's text in each language the lobby ships. */
    private val texts =
        listOf("en", "de", "fr", "es").associateWith {
            SettingsMenu.textFor(messages, Locale.forLanguageTag(it))
        }
    private val english = texts.getValue("en")

    // General: every menu language × every chosen language (the longest decides whether a row
    // fits). Every other tab: every menu language × both toggle states. The region tab's own
    // states are RegionTabTest's.
    private val variants =
        texts.values.flatMap { text ->
            SettingsMenu.LANGUAGES.indices.map { language ->
                SettingsMenu.layout(SettingsMenu.State(language, null, true, true), text)
            } +
                SettingsMenu.Tab.entries
                    .filter { it != SettingsMenu.Tab.GENERAL }
                    .flatMap { tab ->
                        listOf(true, false).flatMap { on ->
                            VOICE_STATES.map { status ->
                                val state =
                                    SettingsMenu.State(0, null, on, !on, voiceStatus = status)
                                        .apply {
                                            this.tab = tab
                                            globalChat = !on
                                            voice = VoicePreferences(on, !on)
                                            previews.replaceAll { _, _ -> on }
                                        }
                                SettingsMenu.layout(state, text)
                            }
                        }
                    }
        }

    @Test
    fun `every tab label fits its button, in every language`() {
        for ((language, text) in texts) {
            for (tab in SettingsMenu.Tab.entries) {
                val width = MenuFonts.textWidth(text(tab.label))
                assertTrue(
                    width <= MenuArt.NAV_BUTTON_W - 6,
                    "$language: '${text(tab.label)}' is $width px wide",
                )
            }
        }
    }

    @Test
    fun `every tab is reachable from the rail and shows only its own rows`() {
        val state = SettingsMenu.State(0, null, true, true)
        for (tab in SettingsMenu.Tab.entries) {
            state.tab = tab
            val hits = SettingsMenu.layout(state, english).elements.filterIsInstance<Element.Hit>()
            // the rail offers every tab, whichever is open
            for (other in SettingsMenu.Tab.entries) {
                assertTrue(hits.any { it.action == other.key }, "$tab: no way to ${other.name}")
            }
            for (preview in SettingsMenu.Preview.entries) {
                assertEquals(
                    preview in tab.previews,
                    hits.any { it.action == preview.key },
                    "$tab: ${preview.name}",
                )
            }
        }
    }

    @Test
    fun `the chat tab's global chat row is a stored setting, not a preview`() {
        val state = SettingsMenu.State(0, null, true, true).apply { tab = SettingsMenu.Tab.CHAT }
        val canvas = SettingsMenu.layout(state, english)
        val hits = canvas.elements.filterIsInstance<Element.Hit>()
        assertTrue(hits.any { it.action?.value() == "menu/settings/global-chat" })
        val labels = canvas.elements.filterIsInstance<Element.Label>().map { it.text }
        assertTrue(english(SettingsMessage.SETTINGS_GLOBAL_CHAT) in labels)
        // its row is first, the previews below
        assertEquals(1, SettingsMenu.Tab.CHAT.previews.size)
        assertTrue(hits.any { it.action?.value() == "menu/settings/mention-sounds" })
    }

    /** A row's label and its value share one strip; in no language may they run into each other. */
    @Test
    fun `labels and values never overlap, in any language`() {
        for (canvas in variants) {
            val labels = canvas.elements.filterIsInstance<Element.Label>()
            for (row in labels.groupBy { it.y }.values.filter { it.size > 1 }) {
                val (left, right) = row.sortedBy { it.x }
                assertTrue(
                    left.x + left.width + 4 <= right.x,
                    "'${left.text}' runs into '${right.text}'",
                )
            }
        }
    }

    @Test
    fun `each language reads its own bundle, not the English fallback`() {
        val title = SettingsMessage.SETTINGS_TITLE
        assertEquals("Settings", english(title))
        assertEquals("Einstellungen", texts.getValue("de")(title))
        assertEquals("Options", texts.getValue("fr")(title))
        assertEquals("Ajustes", texts.getValue("es")(title))
    }

    @Test
    fun `every menu text exists in every language`() {
        for ((language, text) in texts) {
            for (key in SettingsMessage.entries.filter { it.id.startsWith("settings.") }) {
                assertFalse(text(key).isBlank(), "$language: ${key.id} is empty")
            }
        }
    }

    @Test
    fun `lines are well formed for every state`() = variants.forEach(ClientSim::assertWellFormed)

    @Test fun `every hit is exact`() = variants.forEach(ClientSim::assertHitsExact)

    @Test fun `no label is painted over`() = variants.forEach(ClientSim::assertNoCoveredText)

    @Test
    fun `a stored language maps to its entry, by language only`() {
        val index = { tag: String? -> SettingsMenu.LANGUAGES[SettingsMenu.languageIndex(tag)].name }
        assertEquals("Client default", index(null))
        assertEquals("Deutsch", index("de"))
        assertEquals("Deutsch", index("de-DE"))
        assertEquals("Español", index("es"))
        assertEquals("Client default", index("ja"))
    }

    @Test
    fun `a token resolves once, and only for its player`() {
        val tokens = ClickTokens()
        val player = UUID.randomUUID()
        val action = net.kyori.adventure.key.Key.key("grounds", "menu/settings/friend-requests")
        val token = tokens.revision(player)(action)
        assertEquals(null, tokens.consume(UUID.randomUUID(), token))
        assertEquals(action, tokens.consume(player, token))
        assertEquals(null, tokens.consume(player, token))
    }

    @Test
    fun `the chat and voice tab stores party voice and voice hints, then shows the voice`() {
        val state = SettingsMenu.State(0, null, true, true).apply { tab = SettingsMenu.Tab.CHAT }
        val canvas = SettingsMenu.layout(state, english)
        val actions =
            canvas.elements.filterIsInstance<Element.Hit>().mapNotNull { it.action?.value() }
        assertTrue("menu/settings/party-voice" in actions)
        assertTrue("menu/settings/voice-hints" in actions)
        val labels = canvas.elements.filterIsInstance<Element.Label>().map { it.text }
        assertTrue(english(SettingsMessage.SETTINGS_VOICE) in labels)
        assertEquals("Chat & Voice", english(SettingsMenu.Tab.CHAT.label))
    }

    @Test
    fun `the voice row says where the voice is, or why there is none`() {
        assertEquals("Not on this server", SettingsMenu.voiceName(null, english))
        assertEquals("No mod connected", SettingsMenu.voiceName(VoiceStatus(false, null), english))
        assertEquals("Nearby", SettingsMenu.voiceName(VoiceStatus(true, null), english))
        assertEquals("Team Red", SettingsMenu.voiceName(VoiceStatus(true, "Team Red"), english))
    }

    @Test
    fun `a region shows by name, an unknown one as its code`() {
        assertEquals("Nearest", SettingsMenu.regionName(null, english))
        assertEquals("Europe", SettingsMenu.regionName("eu", english))
        assertEquals("XX", SettingsMenu.regionName("xx", english))
    }

    private companion object {
        val VOICE_STATES =
            listOf(
                null,
                VoiceStatus(connected = false, group = null),
                VoiceStatus(connected = true, group = null),
                VoiceStatus(connected = true, group = "Team Red"),
            )
    }
}
