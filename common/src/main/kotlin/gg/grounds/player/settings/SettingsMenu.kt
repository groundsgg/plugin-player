package gg.grounds.player.settings

import gg.grounds.i18n.Prefix
import gg.grounds.i18n.SystemMessageRenderer
import gg.grounds.i18n.Translations
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.slf4j.LoggerFactory

/**
 * `/settings`: the player's network settings, drawn inside a vanilla dialog body with the
 * grounds-global PackSet's menu fonts.
 *
 * Only settings the network actually stores are offered: the language and the default region
 * (service-player), and whether friend requests and private messages are accepted (service-social).
 * All are read when the menu opens and written on every click. The click updates the menu straight
 * away and the write follows off the tick thread; a failed write puts the old value back and says
 * so.
 *
 * Language and region take effect at the player's next login: the proxy reads both on join.
 *
 * The same menu on every server: it builds text and a [SettingsDialog], and [platform] shows it
 * with the engine's own dialog API (Minestom or Paper).
 */
class SettingsMenu<P : Audience>(
    private val platform: SettingsPlatform<P>,
    private val client: PlayerSettingsClient,
    private val messages: Translations,
    /** Region codes that are up right now; only the lobby knows, elsewhere the list is empty. */
    private val liveRegions: () -> Collection<String> = { emptyList() },
    /** Runs after a language choice was stored; the tag is null for "follow the client". */
    private val onLanguageSaved: (P, String?) -> Unit = { _, _ -> },
    /** This server's voice chat, or null when it has none. Read per use: modules install late. */
    private val voiceChat: () -> VoiceSettings? = { null },
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(SettingsMenu::class.java)

    // One thread: settings change a few times per session, and a pool would only add ways for a
    // slow service to eat memory. It also keeps a player's writes in click order.
    private val requests =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "player-settings").apply { isDaemon = true }
        }

    private val tokens = ClickTokens()
    private val states = ConcurrentHashMap<UUID, State>()

    internal class State(
        var language: Int,
        /**
         * null = nearest; otherwise the stored code, which need not be a continent the map knows.
         */
        var region: String?,
        var friendRequests: Boolean,
        var privateMessages: Boolean,
        var tab: Tab = Tab.GENERAL,
        /** Region codes that were up when the menu opened, for the region tab's tooltips. */
        val live: Set<String> = emptySet(),
        /** Preview toggles of the tabs that have no backend yet; kept for this menu only. */
        val previews: MutableMap<Preview, Boolean> =
            Preview.entries.associateWith { true }.toMutableMap(),
        var globalChat: Boolean = true,
        var voice: VoicePreferences = VoicePreferences(partyVoice = true, hints = true),
        /** The player's voice when the menu opened; null when this server has no voice chat. */
        val voiceStatus: VoiceStatus? = null,
        var mentionSounds: Boolean = true,
    )

    /**
     * The tabs down the left. [GENERAL], [REGION], [SOCIAL] and the chat tab's global chat and
     * voice rows are real; the rest are previews for now.
     */
    enum class Tab(
        internal val label: SettingsMessage,
        internal val previews: List<Preview> = emptyList(),
    ) {
        GENERAL(SettingsMessage.SETTINGS_GENERAL),
        /** The world map ([RegionTab]); `/region` opens the menu here. */
        REGION(SettingsMessage.REGION_MENU_TITLE),
        SOCIAL(SettingsMessage.SETTINGS_SOCIAL),
        CHAT(SettingsMessage.SETTINGS_TAB_CHAT, listOf(Preview.CHAT_FILTER)),
        ALERTS(
            SettingsMessage.SETTINGS_TAB_ALERTS,
            listOf(Preview.FRIEND_ALERTS, Preview.PARTY_INVITES, Preview.MATCH_SOUND),
        ),
        GAMEPLAY(
            SettingsMessage.SETTINGS_TAB_GAMEPLAY,
            listOf(Preview.SIDEBAR, Preview.PARTICLES, Preview.AUTO_REQUEUE),
        );

        internal val key: Key =
            Key.key(ClickTokens.NAMESPACE, "menu/settings/tab/${name.lowercase()}")
    }

    /**
     * A setting shown to try the layout before it has a backend: it toggles in the open menu and
     * says so in its tooltip, but nothing stores or applies it.
     */
    internal enum class Preview(val title: SettingsMessage, val hint: SettingsMessage) {
        CHAT_FILTER(
            SettingsMessage.SETTINGS_CHAT_FILTER,
            SettingsMessage.SETTINGS_CHAT_FILTER_HINT,
        ),
        FRIEND_ALERTS(
            SettingsMessage.SETTINGS_FRIEND_ALERTS,
            SettingsMessage.SETTINGS_FRIEND_ALERTS_HINT,
        ),
        PARTY_INVITES(
            SettingsMessage.SETTINGS_PARTY_INVITES,
            SettingsMessage.SETTINGS_PARTY_INVITES_HINT,
        ),
        MATCH_SOUND(
            SettingsMessage.SETTINGS_MATCH_SOUND,
            SettingsMessage.SETTINGS_MATCH_SOUND_HINT,
        ),
        SIDEBAR(SettingsMessage.SETTINGS_SIDEBAR, SettingsMessage.SETTINGS_SIDEBAR_HINT),
        PARTICLES(SettingsMessage.SETTINGS_PARTICLES, SettingsMessage.SETTINGS_PARTICLES_HINT),
        AUTO_REQUEUE(
            SettingsMessage.SETTINGS_AUTO_REQUEUE,
            SettingsMessage.SETTINGS_AUTO_REQUEUE_HINT,
        );

        val key: Key = Key.key(ClickTokens.NAMESPACE, "menu/settings/preview/${name.lowercase()}")
    }

    fun open(player: P, tab: Tab = Tab.GENERAL) {
        val id = platform.id(player)
        CompletableFuture.supplyAsync(
                {
                    State(
                        language = languageIndex(client.locale(id)),
                        region = client.region(id),
                        friendRequests = client.friendRequestsEnabled(id),
                        privateMessages = client.privateMessagesEnabled(id),
                        tab = tab,
                        live = liveRegions().map { it.lowercase() }.toSet(),
                        globalChat = client.globalChatEnabled(id),
                        mentionSounds = client.mentionSoundsEnabled(id),
                        voice = client.voicePreferences(id),
                        voiceStatus = voiceChat()?.status(id),
                    )
                },
                requests,
            )
            .whenComplete { state, error ->
                if (error != null) {
                    logger.warn("Settings for {} could not be read", id, error)
                    messages.send(player, SettingsMessage.SETTINGS_UNAVAILABLE)
                    return@whenComplete
                }
                states[id] = state
                show(player, state)
            }
    }

    /**
     * Handles a custom click. Returns false for keys that are not this menu's, so other handlers
     * can have them.
     */
    fun onClick(player: P, key: Key): Boolean {
        val id = platform.id(player)
        if (key == CLOSE) {
            forget(id)
            platform.close(player)
            return true
        }
        if (!tokens.owns(key)) return false
        // a stale (double click, outdated dialog), foreign or forged token resolves to nothing
        val action = tokens.consume(id, key) ?: return true
        val state = states[id] ?: return true
        // Body links have no native button sound. Feedback belongs to accepted clicks only;
        // the vanilla Done button already plays its own sound on the client.
        runCatching { player.playSound(CLICK_SOUND) }
        when (action) {
            LANGUAGE_PREVIOUS,
            LANGUAGE_NEXT -> {
                val before = state.language
                state.language = step(before, LANGUAGES.size, action == LANGUAGE_NEXT)
                val code = LANGUAGES[state.language].code
                save(
                    player,
                    state,
                    {
                        client.setLocale(id, code)
                        // the proxy cached the old language at join; nudge it to read it again
                        platform.sendPluginMessage(player, LOCALE_CHANNEL, ByteArray(0))
                        onLanguageSaved(player, code)
                        // the menu itself switches language too
                        if (states[id] === state) show(player, state)
                    },
                ) {
                    state.language = before
                }
            }
            RegionTab.AUTOMATIC,
            in RegionTab.PICKS -> {
                val before = state.region
                val region = RegionTab.PICKS[action]?.code
                if (region != before) {
                    state.region = region
                    save(player, state, { client.setRegion(id, region) }) { state.region = before }
                }
            }
            FRIEND_REQUESTS -> {
                val enabled = !state.friendRequests
                state.friendRequests = enabled
                save(player, state, { client.setFriendRequestsEnabled(id, enabled) }) {
                    state.friendRequests = !enabled
                }
            }
            PRIVATE_MESSAGES -> {
                val enabled = !state.privateMessages
                state.privateMessages = enabled
                save(player, state, { client.setPrivateMessagesEnabled(id, enabled) }) {
                    state.privateMessages = !enabled
                }
            }
            GLOBAL_CHAT -> {
                val enabled = !state.globalChat
                state.globalChat = enabled
                save(
                    player,
                    state,
                    {
                        client.setGlobalChatEnabled(id, enabled)
                        // the proxy cached the setting at join; nudge it to read it again
                        platform.sendPluginMessage(player, CHAT_SETTINGS_CHANNEL, ByteArray(0))
                    },
                ) {
                    state.globalChat = !enabled
                }
            }
            MENTION_SOUNDS -> {
                val enabled = !state.mentionSounds
                state.mentionSounds = enabled
                save(
                    player,
                    state,
                    {
                        client.setMentionSoundsEnabled(id, enabled)
                        platform.sendPluginMessage(player, CHAT_SETTINGS_CHANNEL, ByteArray(0))
                    },
                ) {
                    state.mentionSounds = !enabled
                }
            }
            PARTY_VOICE,
            VOICE_HINTS -> {
                val before = state.voice
                val after =
                    if (action == PARTY_VOICE) before.copy(partyVoice = !before.partyVoice)
                    else before.copy(hints = !before.hints)
                state.voice = after
                save(
                    player,
                    state,
                    {
                        client.setVoicePreferences(id, after)
                        // plugin-voice reads them at join; this server's applies them now
                        voiceChat()?.preferencesChanged(id, after)
                    },
                ) {
                    state.voice = before
                }
            }
            else -> {
                Tab.entries.firstOrNull { it.key == action }?.let { state.tab = it }
                // only a preview of the open tab can be toggled; others are not on screen
                state.tab.previews
                    .firstOrNull { it.key == action }
                    ?.let { state.previews[it] = !(state.previews[it] ?: true) }
            }
        }
        // Accepting the click consumed the shown dialog's tokens, so it is always replaced.
        show(player, state)
        return true
    }

    fun forget(player: UUID) {
        tokens.forget(player)
        states.remove(player)
    }

    override fun close() {
        requests.shutdownNow()
    }

    private fun save(player: P, state: State, write: () -> Unit, undo: () -> Unit) {
        CompletableFuture.runAsync(write, requests).whenComplete { _, error ->
            if (error == null) return@whenComplete
            val id = platform.id(player)
            logger.warn("A setting for {} could not be saved", id, error)
            undo()
            messages.send(player, SettingsMessage.SETTINGS_SAVE_FAILED)
            // only while the menu is still open; a closed one stays closed
            if (states[id] === state) show(player, state)
        }
    }

    private fun show(player: P, state: State) {
        // the language the menu has chosen, so a new choice shows straight away; else the client's
        val text =
            textFor(
                messages,
                LANGUAGES[state.language].code?.let(Locale::forLanguageTag)
                    ?: messages.localeOf(player),
            )
        platform.show(
            player,
            dialog(layout(state, text), tokens.revision(platform.id(player)), text),
        )
    }

    internal class Language(val name: String, val code: String?)

    companion object {
        /** Named in their own language. Codes match the proxy's `/lang` (plugin-player). */
        internal val LANGUAGES =
            listOf(
                Language("Client default", null),
                Language("English", "en"),
                Language("Deutsch", "de"),
                Language("Français", "fr"),
                Language("Español", "es"),
            )

        /** Region codes are continents (`<code>.geo.<domain>`); unknown ones show as the code. */
        private val REGION_NAMES =
            mapOf(
                "eu" to SettingsMessage.SETTINGS_REGION_EU,
                "na" to SettingsMessage.SETTINGS_REGION_NA,
                "sa" to SettingsMessage.SETTINGS_REGION_SA,
                "as" to SettingsMessage.SETTINGS_REGION_AS,
                "oc" to SettingsMessage.SETTINGS_REGION_OC,
                "af" to SettingsMessage.SETTINGS_REGION_AF,
            )

        /**
         * The menu's text, its chat lines in the Settings category. [chatFontReady] says whether
         * the player's pack can draw the category's logo.
         */
        fun messages(chatFontReady: (Audience) -> Boolean): Translations =
            Translations.forBundleWithSystemRenderer(
                "gg.grounds.player.settings.messages",
                SettingsMenu::class.java.classLoader,
                SystemMessageRenderer.withLogoReadiness(chatFontReady),
                prefix = Prefix.category("Settings"),
            )

        /** The menu draws plain strings: [key] rendered in [locale], markup dropped. */
        internal fun textFor(messages: Translations, locale: Locale): (SettingsMessage) -> String =
            { key ->
                PlainTextComponentSerializer.plainText().serialize(messages.render(key, locale))
            }

        private val CLICK_SOUND =
            Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 1.0f, 1.0f)
        private val CLOSE: Key = Key.key(ClickTokens.NAMESPACE, "menu/close")

        /**
         * plugin-player on the proxy listens here: an empty message makes it read the player's
         * language from service-player again instead of keeping what it cached at join.
         */
        private const val LOCALE_CHANNEL = "grounds:locale"
        /** The same for plugin-chat and the global chat setting. */
        private const val CHAT_SETTINGS_CHANNEL = "grounds:chat-settings"
        private val LANGUAGE_PREVIOUS: Key =
            Key.key(ClickTokens.NAMESPACE, "menu/settings/language/previous")
        private val LANGUAGE_NEXT: Key =
            Key.key(ClickTokens.NAMESPACE, "menu/settings/language/next")
        private val FRIEND_REQUESTS: Key =
            Key.key(ClickTokens.NAMESPACE, "menu/settings/friend-requests")
        private val PRIVATE_MESSAGES: Key =
            Key.key(ClickTokens.NAMESPACE, "menu/settings/private-messages")
        private val MENTION_SOUNDS: Key =
            Key.key(ClickTokens.NAMESPACE, "menu/settings/mention-sounds")
        private val GLOBAL_CHAT: Key = Key.key(ClickTokens.NAMESPACE, "menu/settings/global-chat")
        private val PARTY_VOICE: Key = Key.key(ClickTokens.NAMESPACE, "menu/settings/party-voice")
        private val VOICE_HINTS: Key = Key.key(ClickTokens.NAMESPACE, "menu/settings/voice-hints")

        /**
         * Extra body width beyond the canvas. The client wraps body text at less than the declared
         * width, and a canvas line reaches the full canvas width while laying out its click spans;
         * without slack every line wraps once.
         */
        private const val BODY_SLACK = 32

        // vanilla GUI text: dark grey without shadow on the panel, white with shadow on rows
        private val DARK: TextColor = TextColor.color(0x404040)
        // the divider's colour, dark enough to read on the light panel without a shadow
        private val ACCENT: TextColor = TextColor.color(0x1f6f69)
        /** The content panel starts right of the tabs; its rows sit 9 px in. */
        private const val CX = MenuArt.NAV_W + 2
        internal const val X = CX + 9
        /** Row width: as wide as the region map, the widest content. */
        private const val W = MenuArt.ROW_W
        private const val NAV_X = 6
        // rows on the 9 px grid, one free line between them
        private const val ROW_Y = 36
        private const val ROW_PITCH = 27

        private fun step(index: Int, size: Int, forward: Boolean) =
            Math.floorMod(index + if (forward) 1 else -1, size)

        /**
         * A stored tag such as `de-DE` matches by language; anything unknown shows as the client's.
         */
        internal fun languageIndex(locale: String?): Int {
            if (locale == null) return 0
            val language = Locale.forLanguageTag(locale).language
            return LANGUAGES.indexOfFirst { it.code == language }.coerceAtLeast(0)
        }

        internal fun regionName(code: String?, text: (SettingsMessage) -> String): String =
            if (code == null) text(SettingsMessage.SETTINGS_NEAREST)
            else REGION_NAMES[code]?.let(text) ?: code.uppercase()

        /**
         * Tabs down the left, the open tab's settings on the right: a heading in the accent colour
         * over a rule, then its rows (label white, current value yellow).
         */
        internal fun layout(state: State, text: (SettingsMessage) -> String): BodyCanvas {
            // Every tab is the region tab's size, so switching tabs never resizes the dialog. +2: a
            // glyph advances one px past its art, and the running width must never exceed the body
            val c = BodyCanvas(CX + MenuArt.REGION_W + 2, MenuArt.TAB_H)
            c.art(MenuArt.navPanel3, 0, 0)
            c.art(MenuArt.contentLeft, CX, 0)
            c.art(MenuArt.contentRight, CX + MenuArt.REGION_W / 2, 0)
            nav(c, state, text)
            section(c, text(state.tab.label), 9)
            when (state.tab) {
                Tab.GENERAL -> general(c, state, text)
                Tab.REGION -> RegionTab.draw(c, X, state.region, state.live, text)
                Tab.SOCIAL -> social(c, state, text)
                Tab.CHAT -> {
                    toggleRow(
                        c,
                        y = ROW_Y,
                        title = text(SettingsMessage.SETTINGS_GLOBAL_CHAT),
                        description = text(SettingsMessage.SETTINGS_GLOBAL_CHAT_HINT),
                        text = text,
                        on = state.globalChat,
                        action = GLOBAL_CHAT,
                    )
                    toggleRow(
                        c,
                        y = ROW_Y + ROW_PITCH,
                        title = text(SettingsMessage.SETTINGS_PARTY_VOICE),
                        description = text(SettingsMessage.SETTINGS_PARTY_VOICE_HINT),
                        text = text,
                        on = state.voice.partyVoice,
                        action = PARTY_VOICE,
                    )
                    toggleRow(
                        c,
                        y = ROW_Y + 2 * ROW_PITCH,
                        title = text(SettingsMessage.SETTINGS_VOICE_HINTS),
                        description = text(SettingsMessage.SETTINGS_VOICE_HINTS_HINT),
                        text = text,
                        on = state.voice.hints,
                        action = VOICE_HINTS,
                    )
                    infoRow(
                        c,
                        y = ROW_Y + 3 * ROW_PITCH,
                        title = text(SettingsMessage.SETTINGS_VOICE),
                        value = voiceName(state.voiceStatus, text),
                        description = text(SettingsMessage.SETTINGS_VOICE_HINT),
                    )
                    toggleRow(
                        c,
                        y = ROW_Y + 4 * ROW_PITCH,
                        title = text(SettingsMessage.SETTINGS_MENTION_SOUND),
                        description = text(SettingsMessage.SETTINGS_MENTION_SOUND_HINT),
                        text = text,
                        on = state.mentionSounds,
                        action = MENTION_SOUNDS,
                    )
                    previews(c, state, text, first = 5)
                }
                else -> previews(c, state, text, first = 0)
            }
            return c
        }

        /** The open tab's preview rows, from row [first] down. */
        private fun previews(
            c: BodyCanvas,
            state: State,
            text: (SettingsMessage) -> String,
            first: Int,
        ) {
            state.tab.previews.forEachIndexed { i, preview ->
                toggleRow(
                    c,
                    y = ROW_Y + (first + i) * ROW_PITCH,
                    title = text(preview.title),
                    description = text(preview.hint),
                    text = text,
                    on = state.previews[preview] ?: true,
                    action = preview.key,
                    note = text(SettingsMessage.SETTINGS_PREVIEW_ONLY),
                )
            }
        }

        private fun nav(c: BodyCanvas, state: State, text: (SettingsMessage) -> String) {
            Tab.entries.forEachIndexed { i, tab ->
                val y = 9 + i * ROW_PITCH
                val selected = tab == state.tab
                val label = text(tab.label)
                c.art(if (selected) MenuArt.navSelected else MenuArt.navButton, NAV_X, y)
                c.label(
                    label,
                    NAV_X + (MenuArt.NAV_BUTTON_W - MenuFonts.textWidth(label)) / 2 + 1,
                    y + 5,
                    if (selected) DARK else NamedTextColor.WHITE,
                    shadow = !selected,
                )
                c.hit(
                    NAV_X,
                    y,
                    MenuArt.NAV_BUTTON_W,
                    18,
                    tab.key,
                    Component.text(label, NamedTextColor.GRAY),
                )
            }
        }

        private fun general(c: BodyCanvas, state: State, text: (SettingsMessage) -> String) {
            choiceRow(
                c,
                y = ROW_Y,
                title = text(SettingsMessage.SETTINGS_LANGUAGE),
                value =
                    LANGUAGES[state.language].let {
                        if (it.code == null) text(SettingsMessage.SETTINGS_CLIENT_DEFAULT)
                        else it.name
                    },
                description = text(SettingsMessage.SETTINGS_LANGUAGE_HINT),
                note = text(SettingsMessage.SETTINGS_LANGUAGE_NOTE),
                text = text,
                previous = LANGUAGE_PREVIOUS,
                next = LANGUAGE_NEXT,
            )
        }

        private fun social(c: BodyCanvas, state: State, text: (SettingsMessage) -> String) {
            toggleRow(
                c,
                y = ROW_Y,
                title = text(SettingsMessage.SETTINGS_FRIEND_REQUESTS),
                description = text(SettingsMessage.SETTINGS_FRIEND_REQUESTS_HINT),
                text = text,
                on = state.friendRequests,
                action = FRIEND_REQUESTS,
            )
            toggleRow(
                c,
                y = ROW_Y + ROW_PITCH,
                title = text(SettingsMessage.SETTINGS_PRIVATE_MESSAGES),
                description = text(SettingsMessage.SETTINGS_PRIVATE_MESSAGES_HINT),
                text = text,
                on = state.privateMessages,
                action = PRIVATE_MESSAGES,
            )
        }

        /** The voice row's value: where the player's voice is, or why there is none. */
        internal fun voiceName(status: VoiceStatus?, text: (SettingsMessage) -> String): String =
            when {
                status == null -> text(SettingsMessage.SETTINGS_VOICE_UNAVAILABLE)
                !status.connected -> text(SettingsMessage.SETTINGS_VOICE_DISCONNECTED)
                else -> status.group ?: text(SettingsMessage.SETTINGS_VOICE_NEARBY)
            }

        /** A heading on the grid line itself (dy 0), with its rule on the next line's top. */
        private fun section(c: BodyCanvas, title: String, y: Int) {
            c.label(title, X - 1, y, ACCENT, shadow = false)
            // the rule spans a wide row: two of the 220 px ones, overlapping
            c.art(MenuArt.divider, X, y + MenuFonts.LINE_HEIGHT)
            c.art(MenuArt.divider, X + MenuArt.ROW_W - 220, y + MenuFonts.LINE_HEIGHT)
        }

        /** A wide row strip, drawn as its two halves. */
        private fun row(c: BodyCanvas, y: Int) {
            c.art(MenuArt.rowWideLeft, X, y)
            c.art(MenuArt.rowWideRight, X + MenuArt.ROW_W / 2, y)
        }

        private fun choiceRow(
            c: BodyCanvas,
            y: Int,
            title: String,
            value: String,
            description: String,
            note: String,
            previous: Key,
            next: Key,
            text: (SettingsMessage) -> String,
        ) {
            row(c, y)
            c.label(title, X + 6, y + 5)
            c.label(value, X + W - 42 - MenuFonts.textWidth(value), y + 5, NamedTextColor.YELLOW)
            c.hit(
                X,
                y,
                W - 38,
                18,
                null, // tooltip only, the arrows change it
                Component.text()
                    .append(Component.text(description, NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text(note, NamedTextColor.DARK_GRAY))
                    .build(),
            )
            c.art(MenuArt.arrowLeft, X + W - 36, y + 2)
            c.hit(
                X + W - 37,
                y,
                16,
                18,
                previous,
                Component.text(text(SettingsMessage.SETTINGS_PREVIOUS), NamedTextColor.GRAY),
            )
            c.art(MenuArt.arrowRight, X + W - 18, y + 2)
            c.hit(
                X + W - 19,
                y,
                16,
                18,
                next,
                Component.text(text(SettingsMessage.SETTINGS_NEXT), NamedTextColor.GRAY),
            )
        }

        /** A row that only shows a value: nothing to click, the tooltip explains it. */
        private fun infoRow(
            c: BodyCanvas,
            y: Int,
            title: String,
            value: String,
            description: String,
        ) {
            row(c, y)
            c.label(title, X + 6, y + 5)
            c.label(value, X + W - 8 - MenuFonts.textWidth(value), y + 5, NamedTextColor.YELLOW)
            c.hit(X, y, W, 18, null, Component.text(description, NamedTextColor.GRAY))
        }

        private fun toggleRow(
            c: BodyCanvas,
            y: Int,
            title: String,
            description: String,
            on: Boolean,
            action: Key,
            text: (SettingsMessage) -> String,
            /** A third tooltip line, for previews that are not stored. */
            note: String? = null,
        ) {
            row(c, y)
            c.label(title, X + 6, y + 5)
            c.art(if (on) MenuArt.toggleOn else MenuArt.toggleOff, X + W - 32, y + 2)
            val tooltip =
                Component.text()
                    .append(Component.text(description, NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(
                        if (on)
                            Component.text(text(SettingsMessage.SETTINGS_ON), NamedTextColor.GREEN)
                        else Component.text(text(SettingsMessage.SETTINGS_OFF), NamedTextColor.RED)
                    )
            if (note != null) {
                tooltip.append(Component.newline())
                tooltip.append(Component.text(note, NamedTextColor.DARK_GRAY))
            }
            c.hit(X, y, W, 18, action, tooltip.build())
        }

        internal fun dialog(
            canvas: BodyCanvas,
            clickKey: (Key) -> Key,
            text: (SettingsMessage) -> String,
        ) =
            SettingsDialog(
                title = Component.text(text(SettingsMessage.SETTINGS_TITLE)),
                body = canvas.render(clickKey),
                width = canvas.width + BODY_SLACK,
                done = Component.text(text(SettingsMessage.SETTINGS_DONE)),
                close = CLOSE,
            )
    }
}
