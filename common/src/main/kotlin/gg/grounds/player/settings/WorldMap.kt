package gg.grounds.player.settings

/**
 * The world map `/region` draws: which pixel belongs to which continent, and where the datacentres
 * sit on it.
 *
 * The land comes from `world-map.txt`, Natural Earth's 1:110m countries rasterised once into an
 * equirectangular grid of [WIDTH] × [height] px (84°N to 56°S, Antarctica left out), one character
 * per pixel: `.` sea, otherwise the continent's [Continent.mark]. Both sides read the same file —
 * the pack draws one sprite per continent from it, the menu places those sprites and lays its click
 * areas over the same pixels — so the two cannot drift. The tip of Chukotka west of the date line
 * is left out: it would stretch Asia's sprite across the whole map, past a glyph's 256 px.
 */
internal object WorldMap {
    const val WIDTH = 330
    private const val TOP_LAT = 84.0
    private const val SCALE = WIDTH / 360.0

    /**
     * A continent as a region code (`<code>.geo.<domain>`, the same codes `/settings` stores). Only
     * [pickable] ones can be picked; the rest are drawn, with their datacentres, but have no geo
     * address to steer to yet.
     */
    enum class Continent(
        val code: String,
        val mark: Char,
        val title: SettingsMessage,
        /** Where its name tag sits (latitude, longitude): on land, clear of the datacentre dots. */
        val labelLat: Double,
        val labelLon: Double,
        /**
         * Fixed, not derived from [zones]: the menu pack draws a highlighted sprite per pickable
         * continent, and its glyphs are numbered in order, so changing the set renumbers every
         * sprite after them and breaks the shipped pack.
         */
        val pickable: Boolean,
    ) {
        NORTH_AMERICA("na", 'n', SettingsMessage.SETTINGS_REGION_NA, 58.0, -102.0, true),
        SOUTH_AMERICA("sa", 's', SettingsMessage.SETTINGS_REGION_SA, -14.0, -58.0, false),
        EUROPE("eu", 'e', SettingsMessage.SETTINGS_REGION_EU, 52.0, 46.0, true),
        AFRICA("af", 'f', SettingsMessage.SETTINGS_REGION_AF, 8.0, 20.0, false),
        ASIA("as", 'a', SettingsMessage.SETTINGS_REGION_AS, 58.0, 100.0, true),
        OCEANIA("oc", 'o', SettingsMessage.SETTINGS_REGION_OC, -24.0, 134.0, true);

        val zones: List<Zone>
            get() = ZONES.filter { it.continent == this }

        companion object {
            fun of(code: String?): Continent? = entries.firstOrNull { it.code.equals(code, true) }
        }
    }

    /** A Vultr region. Players pick its continent; geo-steering picks the region. */
    data class Zone(
        val code: String,
        val city: String,
        val continent: Continent,
        val lat: Double,
        val lon: Double,
    )

    val ZONES =
        listOf(
            Zone("sea", "Seattle", Continent.NORTH_AMERICA, 47.61, -122.33),
            Zone("sjc", "Silicon Valley", Continent.NORTH_AMERICA, 37.34, -121.89),
            Zone("lax", "Los Angeles", Continent.NORTH_AMERICA, 34.05, -118.24),
            Zone("hnl", "Honolulu", Continent.NORTH_AMERICA, 21.31, -157.86),
            Zone("mex", "Mexico City", Continent.NORTH_AMERICA, 19.43, -99.13),
            Zone("dfw", "Dallas", Continent.NORTH_AMERICA, 32.78, -96.80),
            Zone("ord", "Chicago", Continent.NORTH_AMERICA, 41.88, -87.63),
            Zone("atl", "Atlanta", Continent.NORTH_AMERICA, 33.75, -84.39),
            Zone("mia", "Miami", Continent.NORTH_AMERICA, 25.76, -80.19),
            Zone("yto", "Toronto", Continent.NORTH_AMERICA, 43.65, -79.38),
            Zone("ewr", "New Jersey", Continent.NORTH_AMERICA, 40.55, -74.46),
            Zone("scl", "Santiago", Continent.SOUTH_AMERICA, -33.45, -70.67),
            Zone("sao", "São Paulo", Continent.SOUTH_AMERICA, -23.55, -46.63),
            Zone("mad", "Madrid", Continent.EUROPE, 40.42, -3.70),
            Zone("man", "Manchester", Continent.EUROPE, 53.48, -2.24),
            Zone("lhr", "London", Continent.EUROPE, 51.51, -0.13),
            Zone("cdg", "Paris", Continent.EUROPE, 48.86, 2.35),
            Zone("ams", "Amsterdam", Continent.EUROPE, 52.37, 4.90),
            Zone("fra", "Frankfurt", Continent.EUROPE, 50.11, 8.68),
            Zone("mxp", "Milan", Continent.EUROPE, 45.46, 9.19),
            Zone("sto", "Stockholm", Continent.EUROPE, 59.33, 18.07),
            Zone("waw", "Warsaw", Continent.EUROPE, 52.23, 21.01),
            Zone("jnb", "Johannesburg", Continent.AFRICA, -26.20, 28.05),
            Zone("tlv", "Tel Aviv", Continent.ASIA, 32.09, 34.78),
            Zone("bom", "Mumbai", Continent.ASIA, 19.08, 72.88),
            Zone("del", "Delhi NCR", Continent.ASIA, 28.61, 77.21),
            Zone("blr", "Bangalore", Continent.ASIA, 12.97, 77.59),
            Zone("sgp", "Singapore", Continent.ASIA, 1.35, 103.82),
            Zone("icn", "Seoul", Continent.ASIA, 37.57, 126.98),
            Zone("itm", "Osaka", Continent.ASIA, 34.69, 135.50),
            Zone("nrt", "Tokyo", Continent.ASIA, 35.68, 139.69),
            Zone("mel", "Melbourne", Continent.OCEANIA, -37.81, 144.96),
            Zone("syd", "Sydney", Continent.OCEANIA, -33.87, 151.21),
        )

    /** Rows of [WIDTH] characters, top to bottom. */
    val rows: List<String> by lazy {
        val stream =
            WorldMap::class.java.getResourceAsStream("world-map.txt")
                ?: error("world-map.txt missing next to WorldMap")
        stream
            .bufferedReader()
            .readLines()
            .filter { it.isNotEmpty() }
            .also { rows ->
                require(rows.all { it.length == WIDTH }) {
                    "world-map.txt rows must be $WIDTH wide"
                }
            }
    }

    val height: Int
        get() = rows.size

    fun at(x: Int, y: Int): Continent? {
        val mark = rows[y][x]
        return Continent.entries.firstOrNull { it.mark == mark }
    }

    /** The smallest rectangle holding every pixel of [continent], in map pixels. */
    data class Bounds(val x: Int, val y: Int, val w: Int, val h: Int)

    fun bounds(continent: Continent): Bounds = allBounds.getValue(continent)

    private val allBounds: Map<Continent, Bounds> by lazy {
        Continent.entries.associateWith(::measure)
    }

    private fun measure(continent: Continent): Bounds {
        var x0 = WIDTH
        var y0 = height
        var x1 = -1
        var y1 = -1
        for (y in 0 until height) {
            for (x in 0 until WIDTH) {
                if (rows[y][x] != continent.mark) continue
                x0 = minOf(x0, x)
                y0 = minOf(y0, y)
                x1 = maxOf(x1, x)
                y1 = maxOf(y1, y)
            }
        }
        return Bounds(x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }

    /** Where ([lat], [lon]) falls on the map, in map pixels. */
    fun project(lat: Double, lon: Double): Pair<Int, Int> =
        ((lon + 180) * SCALE).toInt() to ((TOP_LAT - lat) * SCALE).toInt()

    /**
     * Each continent's click area on one band of rows (a body line): the runs of columns where that
     * continent has the most land within the band, with sea gaps up to [BRIDGE] px closed so a
     * strait or an island chain does not leave holes. Runs never overlap, which is what lets
     * [BodyCanvas] put several on one line.
     */
    fun runs(top: Int, rowsInBand: Int): List<Triple<Continent, Int, Int>> {
        val owner =
            (0 until WIDTH).map { x ->
                (top until minOf(top + rowsInBand, height))
                    .mapNotNull { y -> at(x, y) }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
            }
        val runs = mutableListOf<Triple<Continent, Int, Int>>()
        var x = 0
        while (x < WIDTH) {
            val continent = owner[x]
            if (continent == null) {
                x++
                continue
            }
            val start = x
            var end = x + 1
            while (true) {
                while (end < WIDTH && owner[end] == continent) end++
                val gapEnd =
                    (end until minOf(end + BRIDGE + 1, WIDTH)).firstOrNull { owner[it] != null }
                if (
                    gapEnd != null &&
                        owner[gapEnd] == continent &&
                        owner.subList(end, gapEnd).all { it == null }
                ) {
                    end = gapEnd
                } else {
                    break
                }
            }
            runs += Triple(continent, start, end - start)
            x = end
        }
        return runs
    }

    private const val BRIDGE = 4

    /** The largest a hover card's continent silhouette may be. */
    const val CARD_W = 44
    const val CARD_H = 26

    /**
     * The size of [continent]'s hover-card silhouette: its land scaled down to fit [CARD_W] ×
     * [CARD_H], at least a font glyph's 7 px tall.
     */
    fun cardSize(continent: Continent): Pair<Int, Int> {
        val b = bounds(continent)
        val scale = minOf(CARD_W.toDouble() / b.w, CARD_H.toDouble() / b.h, 1.0)
        return maxOf(1, Math.ceil(b.w * scale).toInt()) to maxOf(7, Math.ceil(b.h * scale).toInt())
    }

    /**
     * Whether the silhouette's pixel ([cx], [cy]) is land: any of [continent]'s pixels in the block
     * it covers. Taking any rather than one sample keeps thin coasts and islands, and makes the
     * last column and row land wherever the continent's own edges are.
     */
    fun cardPixel(continent: Continent, cx: Int, cy: Int): Boolean {
        val b = bounds(continent)
        val (cw, ch) = cardSize(continent)
        val x0 = b.x + cx * b.w / cw
        val x1 = maxOf(x0 + 1, b.x + (cx + 1) * b.w / cw)
        val y0 = b.y + cy * b.h / ch
        val y1 = maxOf(y0 + 1, b.y + (cy + 1) * b.h / ch)
        return (y0 until minOf(y1, height)).any { y ->
            (x0 until minOf(x1, WIDTH)).any { x -> rows[y][x] == continent.mark }
        }
    }
}
