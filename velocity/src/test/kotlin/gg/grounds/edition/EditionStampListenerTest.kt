package gg.grounds.edition

import com.velocitypowered.api.util.GameProfile
import gg.grounds.edition.listener.EditionStampListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger

class EditionStampListenerTest {

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

    /**
     * Two of the three proxies have no Floodgate, and that is the ordinary case rather than a
     * misconfiguration — the listener is simply not registered there.
     */
    @Test
    fun theLookupIsAbsentWithoutFloodgate() {
        assertNull(FloodgateLookup.create(NOPLogger.NOP_LOGGER))
    }
}
