package gg.grounds.player.settings

/**
 * A bitmap in the [MenuFonts.ART] font. Each sprite is registered nine times, one glyph per 0..8 px
 * drop inside its line, so it can be placed at any pixel height.
 *
 * Only the shape lives here. The pixels are drawn by the test-side `MenuPack` and shipped in the
 * grounds-global PackSet, so [index] and the size must match the pack; `MenuPackTest` pins both.
 */
internal class MenuSprite(val name: String, val index: Int, val width: Int, val height: Int) {
    /**
     * How far the client moves the cursor after this glyph: rightmost opaque column + 1, plus 1 px
     * spacing. Every sprite reaches its right edge, so that is its width + 1.
     */
    val advance: Int
        get() = width + 1

    fun glyph(dy: Int): Char {
        require(dy in 0 until MenuFonts.LINE_HEIGHT)
        return (0xE000 + index * 16 + dy).toChar()
    }
}

/** Every sprite the settings menu draws. Appending is safe; reordering changes every glyph. */
internal object MenuArt {
    private val all = mutableListOf<MenuSprite>()
    val sprites: List<MenuSprite>
        get() = all

    const val PANEL_W = 238
    const val SETTINGS_H = 153

    /**
     * The two-row panel of lobby 2.0.2. No longer drawn, but kept in the pack and in place: a lobby
     * still running that version shares the PackSet with this one, and the pack must serve both.
     */
    @Suppress("unused") val panel = sprite("panel", PANEL_W, 99)
    val row = sprite("row", 220, 18)
    val toggleOn = sprite("toggle_on", 28, 14)
    val toggleOff = sprite("toggle_off", 28, 14)
    val arrowLeft = sprite("arrow_left", 14, 14)
    val arrowRight = sprite("arrow_right", 14, 14)
    /** The four-row panel of lobby 2.0.3; kept for the same reason as [panel]. */
    @Suppress("unused") val settingsPanel = sprite("settings_panel", PANEL_W, SETTINGS_H)

    /**
     * Invisible click areas, one per power of two (1 … 256 px wide, a line tall). The 26.3 client
     * only answers a click inside the bounds of a line's *visible* glyphs and never counts space
     * glyphs, so a hit drawn in the space font was dead wherever no art of its own line happened to
     * reach. These are bitmap glyphs, so they count; their pixels are white at alpha 1/255, which
     * the eye cannot see but the client's "is anything there" test does.
     */
    val hits =
        MenuFonts.powers.map { bit -> sprite("hit_${1 shl bit}", 1 shl bit, MenuFonts.LINE_HEIGHT) }

    /** A rule under a section heading; the line is its top pixel row, the rest is empty. */
    val divider = sprite("divider", 220, 7)

    const val PANEL_H = 180

    /** The single-page panel of lobby 2.2–2.4; kept for the same reason as [panel]. */
    @Suppress("unused") val settingsPanel2 = sprite("settings_panel_2", PANEL_W, PANEL_H)

    // ---- /region ----

    /**
     * The first /region of lobby 2.2.6: a 220 px map in a [PANEL_W] panel. No longer drawn, but
     * kept in the pack in place with their sizes frozen, for a lobby still running that version.
     * The test-side `MenuPack` draws the continents from the old 220 px mask.
     */
    @Suppress("unused")
    val legacyRegion =
        listOf(
            sprite("region_panel", PANEL_W, 162),
            sprite("map_sea", 222, 88),
            sprite("map_na", 97, 47),
            sprite("map_sa", 29, 41),
            sprite("map_eu", 51, 27),
            sprite("map_af", 42, 43),
            sprite("map_as", 220, 56),
            sprite("map_oc", 41, 27),
            sprite("map_na_picked", 97, 47),
            sprite("map_eu_picked", 51, 27),
            sprite("map_as_picked", 220, 56),
            sprite("map_oc_picked", 41, 27),
        )

    /** A datacentre: a 3 px dot on the glyph's top rows (a glyph is at least 7 px tall). */
    val zone = sprite("map_zone", 3, 7)

    /** The /region panel: wider than a glyph can be, so drawn as a left and a right half. */
    const val REGION_W = 352
    const val REGION_H = 189

    val regionPanelLeft = sprite("region_panel_l", REGION_W / 2, REGION_H)
    val regionPanelRight = sprite("region_panel_r", REGION_W / 2, REGION_H)

    /** The sea behind [WorldMap] with a 1 px slot border, in two halves like the panel. */
    const val SEA_W = WorldMap.WIDTH + 2

    val mapSeaLeft = sprite("map_sea_l", SEA_W / 2, WorldMap.height + 2)
    val mapSeaRight = sprite("map_sea_r", SEA_W / 2, WorldMap.height + 2)

    /** Each continent's land at its [WorldMap.bounds]. */
    val continents =
        WorldMap.Continent.entries.associateWith { continent ->
            WorldMap.bounds(continent).let { sprite("map2_${continent.code}", it.w, it.h) }
        }

    /** The same land, highlighted, for a picked continent; for lobbies of that time only. */
    val continentsPicked =
        WorldMap.Continent.FIRST_PICKS.associateWith { continent ->
            WorldMap.bounds(continent).let { sprite("map2_${continent.code}_picked", it.w, it.h) }
        }

    // ---- tabbed settings, after the /region map so its glyphs keep their indices: a tab rail on
    // the left, the open tab's panel right of it ----

    const val NAV_W = 80
    const val NAV_BUTTON_W = 68
    const val TABS_H = 153

    val navPanel = sprite("nav_panel", NAV_W, TABS_H)
    val tabsContentPanel = sprite("tabs_content_panel", PANEL_W, TABS_H)
    val navButton = sprite("nav_button", NAV_BUTTON_W, 18)
    val navSelected = sprite("nav_selected", NAV_BUTTON_W, 18)

    /**
     * /settings with the world map as a tab: every tab is the map's size, so the dialog does not
     * jump when switching. The content panel is [regionPanelLeft] + [regionPanelRight]; the rail
     * grows to match.
     */
    val navPanelTall = sprite("nav_panel_2", NAV_W, REGION_H)

    /** A row across the whole content panel: wider than a glyph can be, so two halves. */
    const val ROW_W = WorldMap.WIDTH

    val rowWideLeft = sprite("row_wide_l", ROW_W / 2, 18)
    val rowWideRight = sprite("row_wide_r", ROW_W / 2, 18)

    // ---- the region tab with depth: shaded land, name tags, a status row and hover cards ----

    /**
     * Every tab's height once the region tab has its two-line status row. A footer line would not
     * fit: 1080p at GUI scale 4 leaves about 195 px for a dialog body before it scrolls.
     */
    const val TAB_H = 195

    val navPanel3 = sprite("nav_panel_3", NAV_W, TAB_H)
    val contentLeft = sprite("tabs_content_3_l", REGION_W / 2, TAB_H)
    val contentRight = sprite("tabs_content_3_r", REGION_W / 2, TAB_H)

    /** The sea with a gradient, shallows along the coasts and the land's drop shadow baked in. */
    val seaLeft = sprite("map_sea3_l", SEA_W / 2, WorldMap.height + 2)
    val seaRight = sprite("map_sea3_r", SEA_W / 2, WorldMap.height + 2)

    /** Each continent in its own colour, lit from the top left. */
    val lands =
        WorldMap.Continent.entries.associateWith { continent ->
            WorldMap.bounds(continent).let { sprite("map3_${continent.code}", it.w, it.h) }
        }

    /**
     * The picked continent in gold with a 1 px white outline, so one px larger on every side: the
     * first pickable ones here, the rest in [landsPickedLater] at the end, so no glyph moves.
     */
    private val landsPickedFirst = WorldMap.Continent.FIRST_PICKS.associateWith(::pickedLand)

    /** The status row under the map: two lines of text beside an icon. */
    const val STATUS_H = 27

    val statusRowLeft = sprite("row_tall_l", ROW_W / 2, STATUS_H)
    val statusRowRight = sprite("row_tall_r", ROW_W / 2, STATUS_H)

    /** Icons in a slot frame, for the status row: a globe (picked) or signal bars (automatic). */
    const val ICON_W = 20

    val iconGlobe = sprite("icon_globe", ICON_W, ICON_W)
    val iconSignal = sprite("icon_signal", ICON_W, ICON_W)

    /** The pointing hand beside the hint. */
    val iconHand = sprite("icon_hand", 9, 9)

    /**
     * Name tags on the map: a dark pill of any width, from two caps and middle strips of 1 … 128 px
     * (like [hits]).
     */
    const val PILL_H = 11

    val pillLeft = sprite("pill_l", 2, PILL_H)
    val pillRight = sprite("pill_r", 2, PILL_H)
    val pillStrips = (0 until 8).map { bit -> sprite("pill_${1 shl bit}", 1 shl bit, PILL_H) }

    /** Each continent's silhouette for its hover card ([WorldMap.cardSize]). */
    val cards =
        WorldMap.Continent.entries.associateWith { continent ->
            WorldMap.cardSize(continent).let { (w, h) -> sprite("card_${continent.code}", w, h) }
        }

    // ---- appended after the region tab shipped: new glyphs go here, never above ----

    /** South America and Africa, pickable since Vultr has datacentres there. */
    private val landsPickedLater =
        WorldMap.Continent.entries
            .filter { it.pickable && it !in WorldMap.Continent.FIRST_PICKS }
            .associateWith(::pickedLand)

    /** Every pickable continent's highlighted land, for [RegionTab]. */
    val landsPicked = landsPickedFirst + landsPickedLater

    private fun pickedLand(continent: WorldMap.Continent) =
        WorldMap.bounds(continent).let {
            sprite("map3_${continent.code}_picked", it.w + 2, it.h + 2)
        }

    private fun sprite(name: String, width: Int, height: Int): MenuSprite {
        // ascent is 7 - dy and must not exceed the height, so a glyph needs to be ≥ 7 px tall
        require(height in 7..256 && width <= 256) { "sprite $name cannot be a font glyph" }
        return MenuSprite(name, all.size, width, height).also { all += it }
    }
}
