package gg.grounds.permissions

import com.velocitypowered.api.permission.PermissionFunction
import com.velocitypowered.api.permission.PermissionProvider
import com.velocitypowered.api.permission.PermissionSubject
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.Player

class CombinedPermissionsProvider(
    private val permissionsCache: PermissionsCache,
    private val fallbackProvider: PermissionProvider,
) : PermissionProvider {
    override fun createFunction(subject: PermissionSubject): PermissionFunction {
        val fallbackFunction = fallbackProvider.createFunction(subject)
        val player = subject as? Player ?: return fallbackFunction
        val cachedFunction = permissionsCache.createPermissionFunction(player.uniqueId)
        return PermissionFunction { permission ->
            val cachedValue = cachedFunction.getPermissionValue(permission)
            if (cachedValue == Tristate.UNDEFINED) {
                fallbackFunction.getPermissionValue(permission)
            } else {
                cachedValue
            }
        }
    }
}
