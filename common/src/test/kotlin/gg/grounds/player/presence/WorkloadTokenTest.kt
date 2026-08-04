package gg.grounds.player.presence

import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WorkloadTokenTest {

    @Test
    fun `a projected token is read and trimmed`() {
        val file = Files.createTempFile("grounds-token", "")
        file.writeText("  a.projected.jwt\n")

        assertEquals("a.projected.jwt", WorkloadToken.loadFrom(file.toString()))
    }

    @Test
    fun `an empty token file is the same as none`() {
        val file = Files.createTempFile("grounds-token", "")
        file.writeText("   \n")

        assertNull(WorkloadToken.loadFrom(file.toString()))
    }

    /** Local development against a service with auth disabled: the request goes out bare. */
    @Test
    fun `a missing token file is not an error`() {
        assertNull(WorkloadToken.loadFrom("/path/that/does/not/exist/token"))
    }
}
