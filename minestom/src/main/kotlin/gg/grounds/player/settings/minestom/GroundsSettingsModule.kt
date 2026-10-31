package gg.grounds.player.settings.minestom

import gg.grounds.minigame.minestom.SidebarLocale
import gg.grounds.minigame.minestom.SidebarVisibility
import gg.grounds.modules.get
import gg.grounds.player.settings.PlayerPreferenceController
import gg.grounds.player.settings.PlayerSettingsClient
import gg.grounds.player.settings.SettingsDialog
import gg.grounds.player.settings.SettingsHooks
import gg.grounds.player.settings.SettingsMenu
import gg.grounds.player.settings.SettingsPlatform
import gg.grounds.player.settings.VoiceSettings
import gg.grounds.player.settings.sidebar.SidebarPreferenceController
import gg.grounds.runtime.GroundsModule
import gg.grounds.runtime.GroundsServerContext
import gg.grounds.runtime.ServerType
import java.util.UUID
import java.util.concurrent.Executors
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.dialog.Dialog
import net.minestom.server.dialog.DialogAction
import net.minestom.server.dialog.DialogActionButton
import net.minestom.server.dialog.DialogAfterAction
import net.minestom.server.dialog.DialogBody
import net.minestom.server.dialog.DialogMetadata
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerCustomClickEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSettingsChangeEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

/**
 * `/settings` on every Minestom server: the lobby and the games. The menu itself is [SettingsMenu];
 * this module shows it as a Minestom dialog and hands it the dialog's clicks.
 *
 * On a lobby it also answers a bare `/region`, the menu on its region tab: the proxy keeps
 * `/region` everywhere else and hands the bare command on only from a lobby.
 */
class GroundsSettingsModule : GroundsModule {
    override val id: String = MODULE_ID

    private val io =
        Executors.newFixedThreadPool(2) {
            Thread(it, "grounds-sidebar-settings").apply { isDaemon = true }
        }
    private var sidebar: SidebarPreferenceController<Player>? = null
    private var languages: PlayerPreferenceController<Player, String?>? = null
    private var invalidations: SettingsInvalidationSubscription? = null
    private var reconcile: Task? = null
    private var menu: SettingsMenu<Player>? = null
    private var node: EventNode<Event>? = null
    private val commands = mutableListOf<Command>()

    override fun install(ctx: GroundsServerContext) {
        // Read per call, not once here: modules install in an order nobody declares, so the lobby
        // may register its hooks after this module installed.
        val hooks = { ctx.services.get<SettingsHooks>() }
        val client = PlayerSettingsClient.fromEnvironment()
        val sidebar =
            SidebarPreferenceController<Player>(
                idOf = { it.uuid },
                read = client::sidebarEnabled,
                dispatch = { MinecraftServer.getSchedulerManager().scheduleNextTick(it) },
                apply = SidebarVisibility::setVisible,
                execute = { if (!io.isShutdown) runCatching { io.execute(it) } },
            )
        this.sidebar = sidebar
        val languages =
            PlayerPreferenceController<Player, String?>(
                idOf = { it.uuid },
                read = client::locale,
                dispatch = { MinecraftServer.getSchedulerManager().scheduleNextTick(it) },
                apply = { player, tag ->
                    SidebarLocale.set(player, tag)
                    try {
                        hooks()?.languageSaved(player.uuid, tag)
                    } catch (failure: Exception) {
                        System.getLogger(GroundsSettingsModule::class.java.name)
                            .log(
                                System.Logger.Level.WARNING,
                                "Stored language hook failed (player=${player.uuid})",
                                failure,
                            )
                    }
                },
                execute = { if (!io.isShutdown) runCatching { io.execute(it) } },
            )
        this.languages = languages
        val refresh: (UUID) -> Unit = { id ->
            sidebar.refresh(id)
            languages.refresh(id)
        }
        val reconcile = {
            sidebar.reconcile()
            languages.reconcile()
        }
        val invalidations = SettingsInvalidationSubscription.fromEnvironment(refresh, reconcile)
        this.invalidations = invalidations
        io.execute {
            runCatching { invalidations.start() }
                .onFailure {
                    System.getLogger(GroundsSettingsModule::class.java.name)
                        .log(
                            System.Logger.Level.WARNING,
                            "Settings invalidations unavailable; periodic reconciliation remains active",
                            it,
                        )
                }
        }
        this.reconcile =
            MinecraftServer.getSchedulerManager()
                .buildTask(reconcile)
                .delay(TaskSchedule.seconds(60))
                .repeat(TaskSchedule.seconds(60))
                .schedule()
        val menu =
            SettingsMenu(
                MinestomSettings,
                client,
                SettingsMenu.messages { (it as? Player)?.getTag(CHAT_FONT_READY) == true },
                liveRegions = { hooks()?.liveRegions().orEmpty() },
                onLanguageSaved = languages::savedAndRefresh,
                voiceChat = { ctx.services.get<VoiceSettings>() },
                onSidebarSaved = sidebar::saved,
            )
        this.menu = menu
        val node = ctx.eventNode("grounds-settings")
        node.addListener(PlayerCustomClickEvent::class.java) { menu.onClick(it.player, it.key) }
        node.addListener(AsyncPlayerConfigurationEvent::class.java) {
            val visibilityRead = sidebar.connect(it.player)
            val languageRead = languages.connect(it.player)
            visibilityRead.run()
            languageRead.run()
        }
        node.addListener(PlayerDisconnectEvent::class.java) {
            sidebar.disconnect(it.player)
            languages.disconnect(it.player)
            menu.forget(it.player.uuid)
        }
        node.addListener(PlayerSettingsChangeEvent::class.java) { event ->
            val player = event.player
            player.scheduleNextTick {
                if (
                    this.languages === languages &&
                        MinecraftServer.getConnectionManager()
                            .getOnlinePlayerByUuid(player.uuid) === player
                )
                    SidebarLocale.refresh(player)
            }
        }
        // ctx.eventNode only creates the node; nothing fires until it hangs off the global handler.
        MinecraftServer.getGlobalEventHandler().addChild(node)
        this.node = node
        register("settings") { menu.open(it) }
        if (ctx.serverType == ServerType.LOBBY) {
            register("region") { menu.open(it, SettingsMenu.Tab.REGION) }
        }
    }

    override fun stop() {
        reconcile?.cancel()
        reconcile = null
        sidebar?.close()
        sidebar = null
        languages?.close()
        languages = null
        invalidations?.close()
        invalidations = null
        io.shutdownNow()
        commands.forEach(MinecraftServer.getCommandManager()::unregister)
        commands.clear()
        node?.let(MinecraftServer.getGlobalEventHandler()::removeChild)
        node = null
        menu?.close()
        menu = null
    }

    private fun register(name: String, open: (Player) -> Unit) {
        val command =
            Command(name).apply {
                setDefaultExecutor { sender, _ -> (sender as? Player)?.let(open) }
            }
        MinecraftServer.getCommandManager().register(command)
        commands += command
    }

    companion object {
        const val MODULE_ID = "grounds.settings"

        /** Set by plugin-chat once the proxy confirmed the player's pack draws the chat logo. */
        private val CHAT_FONT_READY = Tag.Boolean("grounds:chat-font-ready")
    }
}

internal object MinestomSettings : SettingsPlatform<Player> {
    override fun id(player: Player): UUID = player.uuid

    override fun show(player: Player, dialog: SettingsDialog) = player.showDialog(dialog(dialog))

    override fun close(player: Player) = player.closeDialog()

    override fun sendPluginMessage(player: Player, channel: String, message: ByteArray) =
        player.sendPluginMessage(channel, message)

    internal fun dialog(dialog: SettingsDialog): Dialog {
        val meta =
            DialogMetadata(
                dialog.title,
                dialog.title,
                /* canCloseWithEscape = */ true,
                /* pause = */ false,
                // NONE keeps the dialog open after a body click (see SettingsDialog)
                DialogAfterAction.NONE,
                listOf(DialogBody.PlainMessage(dialog.body, dialog.width)),
                emptyList(),
            )
        return Dialog.Notice(
            meta,
            DialogActionButton(
                dialog.done,
                null,
                100,
                DialogAction.Custom(dialog.close, CompoundBinaryTag.empty()),
            ),
        )
    }
}
