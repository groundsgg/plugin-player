package gg.grounds.player.settings.minestom

import gg.grounds.modules.ModuleDescriptor
import gg.grounds.runtime.GroundsModule
import gg.grounds.runtime.GroundsModuleProvider
import gg.grounds.runtime.ServerType

class GroundsSettingsModuleProvider : GroundsModuleProvider {
    override val id: String = GroundsSettingsModule.MODULE_ID
    override val version: String = BuildInfo.VERSION
    override val serverTypes: Set<ServerType> = ServerType.entries.toSet()
    override val descriptor: ModuleDescriptor = ModuleDescriptor(id = id, version = version)

    override fun create(): GroundsModule = GroundsSettingsModule()
}
