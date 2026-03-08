package gg.grounds.permissions.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.permission.PermissionsSetupEvent
import gg.grounds.permissions.CombinedPermissionsProvider
import gg.grounds.permissions.PermissionsCache

class PermissionsSetupListener(private val permissionsCache: PermissionsCache) {
    @Subscribe
    fun onPermissionsSetup(event: PermissionsSetupEvent) {
        event.provider = CombinedPermissionsProvider(permissionsCache, event.provider)
    }
}
