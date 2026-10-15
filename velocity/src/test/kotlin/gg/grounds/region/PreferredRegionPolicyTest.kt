package gg.grounds.region

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreferredRegionPolicyTest {

    private val catalog = RegionCatalog.parse("eu=eu.geo.grnds.io:30565,na=na.geo.grnds.io:30565")
    private val policy = PreferredRegionPolicy(catalog, currentCode = "eu")

    private fun eligible(
        policy: PreferredRegionPolicy = this.policy,
        virtualHost: String? = "play.grounds.gg",
        isTransfer: Boolean = false,
        supportsTransfer: Boolean = true,
        isBedrock: Boolean = false,
    ) = policy.mayRedirect(virtualHost, isTransfer, supportsTransfer, isBedrock)

    @Test
    fun `parses codes hosts and ports, dropping malformed entries`() {
        val parsed = RegionCatalog.parse(" eu=eu.geo.grnds.io:30565 , na=na.host ,bad,x=h:99999,=h")

        assertEquals(listOf("eu", "na"), parsed.regions.map { it.code })
        assertEquals(30565, parsed["EU"]?.port)
        assertEquals(RegionCatalog.DEFAULT_PORT, parsed["na"]?.port)
        assertTrue(RegionCatalog.parse(null).regions.isEmpty())
    }

    @Test
    fun `a player steered to the wrong continent is sent to the preferred one`() {
        assertTrue(eligible())
        assertEquals(RegionCatalog.Region("na", "na.geo.grnds.io", 30565), policy.target("na"))
    }

    @Test
    fun `no preference, the current continent or an unknown code stays`() {
        assertNull(policy.target(null))
        assertNull(policy.target(" "))
        assertNull(policy.target("EU"))
        assertNull(policy.target("ap"))
    }

    /** A transfer always lands on a catalogue host; redirecting from one could loop. */
    @Test
    fun `a connection to a catalogue host is never redirected`() {
        assertFalse(eligible(virtualHost = "na.geo.grnds.io"))
        assertFalse(eligible(virtualHost = "EU.geo.grnds.io."))
    }

    @Test
    fun `transfers, old clients, Bedrock players and unknown hosts are never redirected`() {
        assertFalse(eligible(isTransfer = true))
        assertFalse(eligible(supportsTransfer = false))
        assertFalse(eligible(isBedrock = true))
        assertFalse(eligible(virtualHost = null))
    }

    /** Stage today: one continent, so nothing ever happens and service-player is never asked. */
    @Test
    fun `a catalogue with nowhere else to go never redirects`() {
        val single = PreferredRegionPolicy(RegionCatalog.parse("eu=eu.geo.grnds.io:30565"), "eu")

        assertFalse(eligible(policy = single, virtualHost = "stage.geo.grnds.io"))
    }

    @Test
    fun `a proxy that does not know its own continent never redirects`() {
        val unknown = PreferredRegionPolicy(catalog, currentCode = null)

        assertFalse(eligible(policy = unknown))
        assertNull(unknown.target("na"))
    }

    @Test
    fun `the current code is the continent, falling back to the datacentre`() {
        assertEquals("eu", PreferredRegionPolicy.currentCode("eu", "de-nbg1"))
        assertEquals("de-nbg1", PreferredRegionPolicy.currentCode(" ", "de-nbg1"))
        assertNull(PreferredRegionPolicy.currentCode(null, null))
    }
}
