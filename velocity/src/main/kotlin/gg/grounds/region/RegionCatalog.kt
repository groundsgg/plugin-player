package gg.grounds.region

/**
 * The regions a player can be sent to, read from `REGIONS` (`code=host[:port]`, comma separated,
 * e.g. `eu=eu.geo.grnds.io:30565`). Codes are continents.
 *
 * The same format plugin-proxy's `/region` reads. Its parser lives in plugin-proxy's velocity
 * module, not in `plugin-proxy-api`, so it cannot be shared; this is the subset a redirect needs.
 * Malformed entries are dropped — a redirect to a half-parsed address is worse than none.
 */
class RegionCatalog(val regions: List<Region>) {

    data class Region(val code: String, val host: String, val port: Int)

    operator fun get(code: String): Region? = regions.firstOrNull { it.code.equals(code, true) }

    /** Whether [host] is one of the per-region addresses a transfer lands on. */
    fun isRegionHost(host: String): Boolean =
        normalizeHost(host).let { candidate -> regions.any { normalizeHost(it.host) == candidate } }

    companion object {
        const val DEFAULT_PORT = 25565

        fun parse(raw: String?): RegionCatalog {
            val regions = mutableListOf<Region>()
            raw?.split(',')?.forEach { entry ->
                val code = entry.substringBefore('=', "").trim()
                val target = entry.substringAfter('=', "").trim()
                val host = target.substringBeforeLast(':', target).trim()
                val portText = target.substringAfterLast(':', "").trim()
                val port =
                    if (portText.isEmpty()) DEFAULT_PORT
                    else portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return@forEach
                if (code.isEmpty() || host.isEmpty()) return@forEach
                if (regions.any { it.code.equals(code, true) }) return@forEach
                regions += Region(code, host, port)
            }
            return RegionCatalog(regions)
        }

        private fun normalizeHost(host: String): String = host.trim().trimEnd('.').lowercase()
    }
}
