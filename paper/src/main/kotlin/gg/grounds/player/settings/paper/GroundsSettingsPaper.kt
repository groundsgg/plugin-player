package gg.grounds.player.settings.paper

import gg.grounds.player.settings.PlayerSettingsClient
import gg.grounds.player.settings.SettingsDialog
import gg.grounds.player.settings.SettingsMenu
import gg.grounds.player.settings.SettingsPlatform
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import java.util.UUID
import net.kyori.adventure.nbt.api.BinaryTagHolder
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * `/settings` on Paper servers (the buildserver): the same menu as on Minestom, shown with Paper's
 * dialog API. No lobby hooks here, so the region tab shows no live regions.
 */
class GroundsSettingsPaper : JavaPlugin(), Listener, SettingsPlatform<Player> {
    private var menu: SettingsMenu<Player>? = null

    override fun onEnable() {
        // The menu nudges the proxy on these after a save; Paper drops unregistered channels.
        CHANNELS.forEach { server.messenger.registerOutgoingPluginChannel(this, it) }
        val menu =
            SettingsMenu(
                this,
                PlayerSettingsClient.fromEnvironment(),
                // The buildserver's chat is drawn by the proxy, so no pack readiness is known here.
                SettingsMenu.messages { false },
            )
        this.menu = menu
        server.pluginManager.registerEvents(this, this)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event
                .registrar()
                .register(
                    "settings",
                    BasicCommand { source, _ -> (source.executor as? Player)?.let(menu::open) },
                )
        }
    }

    override fun onDisable() {
        menu?.close()
        menu = null
        server.messenger.unregisterOutgoingPluginChannel(this)
    }

    @EventHandler
    fun onClick(event: PlayerCustomClickEvent) {
        val player = (event.commonConnection as? PlayerGameConnection)?.player ?: return
        menu?.onClick(player, event.identifier)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        menu?.forget(event.player.uniqueId)
    }

    override fun id(player: Player): UUID = player.uniqueId

    override fun show(player: Player, dialog: SettingsDialog) {
        // The menu answers from its request thread, and Paper builds a dialog from its registries:
        // show it on the player's scheduler instead.
        player.scheduler.run(this, { player.showDialog(dialog(dialog)) }, null)
    }

    override fun close(player: Player) = player.closeDialog()

    override fun sendPluginMessage(player: Player, channel: String, message: ByteArray) =
        player.sendPluginMessage(this, channel, message)

    private fun dialog(dialog: SettingsDialog): Dialog =
        Dialog.create { builder ->
            builder
                .empty()
                .base(
                    DialogBase.builder(dialog.title)
                        .canCloseWithEscape(true)
                        .pause(false)
                        // NONE keeps the dialog open after a body click (see SettingsDialog)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(listOf(DialogBody.plainMessage(dialog.body, dialog.width)))
                        .build()
                )
                .type(
                    DialogType.notice(
                        ActionButton.create(
                            dialog.done,
                            null,
                            100,
                            DialogAction.customClick(
                                dialog.close,
                                BinaryTagHolder.binaryTagHolder("{}"),
                            ),
                        )
                    )
                )
        }

    private companion object {
        val CHANNELS = listOf("grounds:locale", "grounds:chat-settings")
    }
}
