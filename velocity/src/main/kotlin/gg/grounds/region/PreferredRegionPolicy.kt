package gg.grounds.region

/**
 * Whether a joining player should be moved to the region they prefer, and where.
 *
 * Split in two so the join path only asks service-player when the answer could matter:
 * [mayRedirect] needs nothing but the connection, [target] needs the stored preference.
 *
 * Every "don't know" resolves to no redirect. A player left on the region geo-steering chose still
 * plays; a wrong redirect, or a loop of them, does not.
 */
class PreferredRegionPolicy(
    private val catalog: RegionCatalog,
    /** The catalogue code this proxy answers for (its continent), or null when unknown. */
    private val currentCode: String?,
) {

    /**
     * False for anyone a transfer must not touch:
     * - a connection that is itself a transfer — this is the loop guard, and it also covers a drain
     *   (a drain cookie only ever arrives on a transfer, and plugin-agones owns that cookie);
     * - a connection addressed to a catalogue host — a transfer always lands on one, so a player
     *   who dialled it directly chose this region, and redirecting them could bounce forever;
     * - an unknown virtual host, a pre-1.20.5 client (no transfer packet), a Bedrock player (Geyser
     *   cannot follow one);
     * - a proxy that does not know its own code, or whose catalogue offers nowhere else to go.
     */
    fun mayRedirect(
        virtualHost: String?,
        isTransfer: Boolean,
        supportsTransfer: Boolean,
        isBedrock: Boolean,
    ): Boolean {
        if (currentCode == null) return false
        if (catalog.regions.none { !it.code.equals(currentCode, true) }) return false
        if (isTransfer || !supportsTransfer || isBedrock) return false
        if (virtualHost.isNullOrBlank() || catalog.isRegionHost(virtualHost)) return false
        return true
    }

    /** The region to transfer to for a stored [preferred] code, or null to stay. */
    fun target(preferred: String?): RegionCatalog.Region? {
        if (currentCode == null || preferred.isNullOrBlank()) return null
        if (preferred.equals(currentCode, true)) return null
        return catalog[preferred.trim()]
    }

    companion object {
        /**
         * `CONTINENT` keys the catalogue; `REGION` (the datacentre) is the fallback for a catalogue
         * still keyed by datacentre — the same rule plugin-proxy's `/region` uses.
         */
        fun currentCode(continent: String?, region: String?): String? =
            continent?.trim()?.takeIf(String::isNotEmpty)
                ?: region?.trim()?.takeIf(String::isNotEmpty)
    }
}
