package gg.grounds.presence

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TransferAdmissionBridgeTest {
    private val profile = UUID.randomUUID()
    private val connection = UUID.randomUUID()

    private fun valid() =
        mapOf(
            "playerId" to profile.toString(),
            "handoffToken" to "v".repeat(43),
            "connectionId" to connection.toString(),
            "destinationProxyId" to "target",
        )

    @Test
    fun `bridge keeps the exact runtime connection identity`() {
        assertEquals(
            TransferAdmissionBridge.Admission("v".repeat(43), connection),
            TransferAdmissionBridge.parse(valid(), profile, "target"),
        )
    }

    @Test
    fun `foreign authenticated profiles and proxies cannot use the reservation`() {
        assertNull(TransferAdmissionBridge.parse(valid(), UUID.randomUUID(), "target"))
        assertNull(TransferAdmissionBridge.parse(valid(), profile, "other"))
        assertNull(TransferAdmissionBridge.parse(valid(), profile, null))
    }

    @Test
    fun `malformed or missing runtime data fails closed`() {
        assertNull(TransferAdmissionBridge.parse(null, profile, "target"))
        assertNull(
            TransferAdmissionBridge.parse(
                valid() + ("connectionId" to "invalid"),
                profile,
                "target",
            )
        )
        assertNull(
            TransferAdmissionBridge.parse(valid() + ("handoffToken" to ""), profile, "target")
        )
    }
}
