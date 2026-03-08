package gg.grounds.permissions.services

import gg.grounds.grpc.permissions.PlayerPermissions
import gg.grounds.permissions.GrpcPermissionsClient
import io.grpc.StatusRuntimeException
import java.util.UUID
import org.slf4j.Logger

class PermissionsService(private val logger: Logger) : AutoCloseable {
    private lateinit var client: GrpcPermissionsClient

    fun configure(target: String) {
        close()
        client = GrpcPermissionsClient.create(target)
    }

    fun fetchPlayerPermissions(playerId: UUID): PlayerPermissions? {
        return try {
            client.getPlayerPermissions(playerId).player
        } catch (e: StatusRuntimeException) {
            logStatusFailure(playerId, null, e)
            null
        } catch (e: RuntimeException) {
            logFailure(
                playerId,
                null,
                "Permissions service request failed",
                e.message ?: e::class.java.name,
            )
            null
        }
    }

    fun checkPlayerPermission(playerId: UUID, permission: String): Boolean? {
        return try {
            client.checkPlayerPermission(playerId, permission).allowed
        } catch (e: StatusRuntimeException) {
            logStatusFailure(playerId, permission, e)
            null
        } catch (e: RuntimeException) {
            logFailure(
                playerId,
                permission,
                "Permissions service request failed",
                e.message ?: e::class.java.name,
            )
            null
        }
    }

    override fun close() {
        if (this::client.isInitialized) {
            client.close()
        }
    }

    private fun logStatusFailure(
        playerId: UUID,
        permission: String?,
        exception: StatusRuntimeException,
    ) {
        val message =
            if (GrpcPermissionsClient.isServiceUnavailable(exception)) {
                "Permissions service unavailable"
            } else {
                "Permissions service request failed"
            }
        logFailure(playerId, permission, message, exception.status)
    }

    private fun logFailure(playerId: UUID, permission: String?, message: String, reason: Any) {
        if (permission == null) {
            logger.warn("$message (playerId={}, reason={})", playerId, reason)
        } else {
            logger.warn(
                "$message (playerId={}, permission={}, reason={})",
                playerId,
                permission,
                reason,
            )
        }
    }
}
