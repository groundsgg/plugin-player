package gg.grounds.edition

import com.velocitypowered.api.util.GameProfile
import gg.grounds.edition.listener.EditionStampListener
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger

class EditionStampListenerTest {

    @BeforeEach
    fun resetFakeFloodgate() {
        FakeFloodgateApi.bedrock = false
        FakeFloodgateApi.fail = false
    }

    private fun textures() = GameProfile.Property("textures", "skin-payload", "skin-signature")

    @Test
    fun marksAProfileThatCarriesNothingYet() {
        val stamped = EditionStampListener.withEditionProperty(emptyList())

        assertNotNull(stamped)
        assertEquals(1, stamped!!.size)
        assertEquals(EditionStampListener.PROPERTY, stamped[0].name)
        assertEquals(EditionStampListener.BEDROCK, stamped[0].value)
    }

    /**
     * The skin is the reason this appends rather than replaces. Floodgate uploads a Bedrock
     * player's skin and puts it here, so a stamp that rebuilt the list would leave them skinless.
     */
    @Test
    fun keepsTheSkinTheProfileAlreadyHad() {
        val stamped = EditionStampListener.withEditionProperty(listOf(textures()))

        assertNotNull(stamped)
        assertEquals(listOf("textures", EditionStampListener.PROPERTY), stamped!!.map { it.name })
        assertEquals("skin-payload", stamped[0].value)
        assertEquals("skin-signature", stamped[0].signature)
    }

    @Test
    fun doesNothingWhenTheMarkerIsAlreadyThere() {
        val already =
            listOf(
                textures(),
                GameProfile.Property(
                    EditionStampListener.PROPERTY,
                    EditionStampListener.BEDROCK,
                    "",
                ),
            )

        assertNull(
            EditionStampListener.withEditionProperty(already),
            "a second stamp would put two markers on the wire",
        )
    }

    @Test
    fun explicitJavaStampReplacesAConflictingMarkerAndKeepsTextures() {
        val existing =
            listOf(
                textures(),
                GameProfile.Property(
                    EditionStampListener.PROPERTY,
                    EditionStampListener.BEDROCK,
                    "",
                ),
            )

        val stamped = EditionStampListener.withEditionProperty(existing, EditionStampListener.JAVA)

        assertEquals(listOf("textures", EditionStampListener.PROPERTY), stamped.map { it.name })
        assertEquals(EditionStampListener.JAVA, stamped.last().value)
    }

    @Test
    fun authoritativeBedrockStampCollapsesDuplicateAndConflictingMarkers() {
        val existing =
            listOf(
                GameProfile.Property(EditionStampListener.PROPERTY, EditionStampListener.JAVA, ""),
                textures(),
                GameProfile.Property(
                    EditionStampListener.PROPERTY,
                    EditionStampListener.BEDROCK,
                    "",
                ),
            )

        val stamped =
            EditionStampListener.withEditionProperty(existing, EditionStampListener.BEDROCK)

        assertEquals(listOf("textures", EditionStampListener.PROPERTY), stamped.map { it.name })
        assertEquals(EditionStampListener.BEDROCK, stamped.last().value)
    }

    @Test
    fun unknownClassificationRemovesAnUnverifiedEditionMarker() {
        val existing =
            listOf(
                textures(),
                GameProfile.Property(EditionStampListener.PROPERTY, EditionStampListener.JAVA, ""),
            )

        val stamped = EditionStampListener.withEditionProperty(existing, null)
        assertEquals(1, stamped.size)
        assertSame(existing.first(), stamped.single())
    }

    /** Missing Floodgate is positive evidence that every connection on this proxy is Java. */
    @Test
    fun missingFloodgateIsKnownJava() {
        val lookup =
            FloodgateLookup.create(NOPLogger.NOP_LOGGER) {
                throw ClassNotFoundException("Floodgate intentionally absent")
            }

        val xuidShapedId = UUID.fromString("00000000-0000-0000-0009-01f64d9a38a8")
        assertEquals(false, lookup.classify(xuidShapedId))
        assertFalse(lookup.isBedrock(xuidShapedId))
    }

    @Test
    fun linkedBedrockAndJavaPlayersUseFloodgateRatherThanUuidShape() {
        FakeFloodgateApi.bedrock = true
        val lookup = FloodgateLookup.create(NOPLogger.NOP_LOGGER) { FakeFloodgateApi::class.java }

        assertEquals(true, lookup.classify(PLAYER_ID))
        assertTrue(lookup.isBedrock(PLAYER_ID))

        FakeFloodgateApi.bedrock = false
        assertEquals(false, lookup.classify(PLAYER_ID))
        assertFalse(lookup.isBedrock(PLAYER_ID))
    }

    @Test
    fun brokenFloodgateShapeIsUnknownRatherThanJava() {
        val lookup = FloodgateLookup.create(NOPLogger.NOP_LOGGER) { BadFloodgateApi::class.java }

        assertNull(lookup.classify(PLAYER_ID))
        assertFalse(lookup.isBedrock(PLAYER_ID))
    }

    @Test
    fun floodgateInvocationFailureIsUnknownButAntiCheatContractRemainsFalse() {
        FakeFloodgateApi.fail = true
        val lookup = FloodgateLookup.create(NOPLogger.NOP_LOGGER) { FakeFloodgateApi::class.java }

        assertNull(lookup.classify(PLAYER_ID))
        assertFalse(lookup.isBedrock(PLAYER_ID))

        FakeFloodgateApi.fail = false
    }

    companion object {
        private val PLAYER_ID = UUID.fromString("67e55044-10b1-426f-9247-bb680e5fe0c8")
    }
}

class FakeFloodgateApi {
    fun isFloodgatePlayer(playerId: UUID): Boolean {
        require(playerId == UUID.fromString("67e55044-10b1-426f-9247-bb680e5fe0c8"))
        if (fail) throw IllegalStateException("Floodgate unavailable")
        return bedrock
    }

    companion object {
        var bedrock: Boolean = false
        var fail: Boolean = false

        @JvmStatic fun getInstance(): FakeFloodgateApi = FakeFloodgateApi()
    }
}

class BadFloodgateApi
