package gg.grounds.player.settings

import gg.grounds.player.settings.WorldMap.Continent
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor

/**
 * The region tab of `/settings`: a world map for picking the default region. Clicking a continent
 * makes it the player's default region; the status row below says what is chosen and hands the
 * choice back to geo-steering.
 *
 * The value is service-player's preferred region, a continent code. A player picks a continent,
 * never a datacentre: the Vultr regions are dots on the map and names on the hover card, because
 * `<continent>.geo` is one address per continent and the steering decides the zone (see
 * [WorldMap]).
 *
 * A dialog body has no hover state — the client neither restyles text under the cursor nor tells a
 * shader where the cursor is — so the hover effect is the tooltip: a card with the continent's
 * silhouette, its datacentres and what a click would do.
 */
internal object RegionTab {
    val AUTOMATIC: Key = Key.key(ClickTokens.NAMESPACE, "menu/region/automatic")

    fun pick(continent: Continent): Key =
        Key.key(ClickTokens.NAMESPACE, "menu/region/pick/${continent.code}")

    /** The continent a pick action chooses; only pickable continents have one. */
    val PICKS: Map<Key, Continent> = Continent.entries.filter { it.pickable }.associateBy(::pick)

    private val GOLD: TextColor = TextColor.color(0xf2c037)
    private val DARK: TextColor = TextColor.color(0x404040)

    /** The map's top; on the line grid so a band of click areas is a body line. */
    const val MAP_Y = 27
    const val ROW_Y = 162

    /** Click areas per body line, from the map alone, so worked out once. */
    private val bands: List<List<Triple<Continent, Int, Int>>> by lazy {
        val lines = (WorldMap.height + MenuFonts.LINE_HEIGHT - 1) / MenuFonts.LINE_HEIGHT
        (0 until lines).map { WorldMap.runs(it * MenuFonts.LINE_HEIGHT, MenuFonts.LINE_HEIGHT) }
    }

    /**
     * Draws the tab with the map's left edge at [x]: the hint beside the heading, the map and the
     * status row. [region] is the stored code (null = nearest), [live] the region codes that are up
     * right now.
     */
    fun draw(
        c: BodyCanvas,
        x: Int,
        region: String?,
        live: Set<String>,
        text: (SettingsMessage) -> String,
    ) {
        val picked = Continent.of(region)
        val right = x + MenuArt.ROW_W

        // beside the heading, right-aligned: a pointing hand and what to do
        val hint = text(SettingsMessage.REGION_MENU_HINT)
        val hintX = right - MenuFonts.textWidth(hint)
        c.art(MenuArt.iconHand, hintX - 12, 9)
        c.label(hint, hintX, 9, DARK, shadow = false)

        // Sea, land, datacentres, then the name tags: a later line paints over an earlier one,
        // and within a line art goes in the order it was added.
        c.art(MenuArt.seaLeft, x - 1, MAP_Y - 1)
        c.art(MenuArt.seaRight, x - 1 + MenuArt.SEA_W / 2, MAP_Y - 1)
        for (continent in Continent.entries) {
            val b = WorldMap.bounds(continent)
            // a stored continent without servers is not lit
            val lit = MenuArt.landsPicked[continent]?.takeIf { continent == picked }
            if (lit != null) c.art(lit, x + b.x - 1, MAP_Y + b.y - 1)
            else c.art(MenuArt.lands.getValue(continent), x + b.x, MAP_Y + b.y)
        }
        for ((zx, zy) in WorldMap.ZONES.map { WorldMap.project(it.lat, it.lon) }.distinct()) {
            c.art(MenuArt.zone, x + zx - 1, MAP_Y + zy - 1)
        }
        for (continent in Continent.entries) nameTag(c, x, continent, continent == picked, text)

        bands.forEachIndexed { line, runs ->
            for ((continent, rx, w) in runs) {
                val action =
                    if (continent.pickable && continent != picked) pick(continent) else null
                c.hit(
                    x + rx,
                    MAP_Y + line * MenuFonts.LINE_HEIGHT,
                    w,
                    MenuFonts.LINE_HEIGHT,
                    action,
                    card(continent, picked, live, text),
                )
            }
        }

        statusRow(c, x, region, picked, text)
    }

    /** The tag's left, top and width for [name] on [continent], kept inside the map. */
    internal fun tagBox(x: Int, continent: Continent, name: String): Triple<Int, Int, Int> {
        val (px, py) = WorldMap.project(continent.labelLat, continent.labelLon)
        val width = MenuFonts.textWidth(name) + 6
        val left = (x + px - width / 2).coerceIn(x, x + WorldMap.WIDTH - width)
        return Triple(left, MAP_Y + py - MenuArt.PILL_H / 2, width)
    }

    /** A dark pill with the continent's name, gold for the picked one. */
    private fun nameTag(
        c: BodyCanvas,
        x: Int,
        continent: Continent,
        picked: Boolean,
        text: (SettingsMessage) -> String,
    ) {
        val name = text(continent.title)
        val (left, top, width) = tagBox(x, continent, name)
        c.art(MenuArt.pillLeft, left, top)
        var cursor = left + 2
        val middle = width - 4
        for ((bit, strip) in MenuArt.pillStrips.withIndex().reversed()) {
            if (middle and (1 shl bit) == 0) continue
            c.art(strip, cursor, top)
            cursor += 1 shl bit
        }
        c.art(MenuArt.pillRight, cursor, top)
        c.label(name, left + 3, top + 2, if (picked) GOLD else NamedTextColor.WHITE, shadow = false)
    }

    /**
     * What is chosen, in words, beside an icon: a globe for a picked region, signal bars for
     * "nearest". The toggle on the right switches back to nearest.
     */
    private fun statusRow(
        c: BodyCanvas,
        x: Int,
        region: String?,
        picked: Continent?,
        text: (SettingsMessage) -> String,
    ) {
        val automatic = region == null
        c.art(MenuArt.statusRowLeft, x, ROW_Y)
        c.art(MenuArt.statusRowRight, x + MenuArt.ROW_W / 2, ROW_Y)
        c.art(if (automatic) MenuArt.iconSignal else MenuArt.iconGlobe, x + 4, ROW_Y + 4)

        val label = "${text(SettingsMessage.SETTINGS_REGION)}:"
        val value = SettingsMenu.regionName(region, text)
        val textX = x + 4 + MenuArt.ICON_W + 6
        c.label(label, textX, ROW_Y + 4)
        c.label(
            value,
            textX + MenuFonts.textWidth(label) + 4,
            ROW_Y + 4,
            if (picked != null) GOLD else NamedTextColor.YELLOW,
        )
        val subtitle =
            if (automatic) SettingsMessage.REGION_MENU_AUTOMATIC_HINT
            else SettingsMessage.REGION_MENU_PREFERRED
        c.label(text(subtitle), textX, ROW_Y + 15, DARK, shadow = false)

        c.art(
            if (automatic) MenuArt.toggleOn else MenuArt.toggleOff,
            x + MenuArt.ROW_W - 32,
            ROW_Y + 7,
        )
        c.hit(
            x,
            ROW_Y,
            MenuArt.ROW_W,
            MenuArt.STATUS_H,
            if (automatic) null else AUTOMATIC,
            Component.text()
                .append(
                    Component.text(
                        text(SettingsMessage.REGION_MENU_AUTOMATIC),
                        NamedTextColor.WHITE,
                    )
                )
                .append(Component.newline())
                .append(
                    Component.text(
                        text(SettingsMessage.REGION_MENU_AUTOMATIC_HINT),
                        NamedTextColor.GRAY,
                    )
                )
                .append(Component.newline())
                .append(
                    if (automatic)
                        Component.text(
                            text(SettingsMessage.REGION_MENU_AUTOMATIC_ON),
                            NamedTextColor.GREEN,
                        )
                    else
                        Component.text(
                            text(SettingsMessage.REGION_MENU_AUTOMATIC_OFF),
                            NamedTextColor.RED,
                        )
                )
                .append(Component.newline())
                .append(note(text))
                .build(),
        )
    }

    private fun note(text: (SettingsMessage) -> String): Component =
        Component.text(text(SettingsMessage.SETTINGS_REGION_NOTE), NamedTextColor.DARK_GRAY)

    /** Datacentre names per tooltip line, so a long list wraps into a column, not off-screen. */
    private const val CITIES_PER_LINE = 3

    /** A tooltip line is 10 px; the card's lines must at least cover its silhouette. */
    private const val TOOLTIP_LINE = 10

    /**
     * The hover card: [continent]'s silhouette on the left, beside it its name, datacentres,
     * whether it is up, and what a click does. Every line after the first is indented past the
     * silhouette with the space font, and the card gets enough lines for the silhouette to fit.
     */
    internal fun card(
        continent: Continent,
        picked: Continent?,
        live: Set<String>,
        text: (SettingsMessage) -> String,
    ): Component {
        val sprite = MenuArt.cards.getValue(continent)
        val indent = sprite.advance + 5
        val lines = mutableListOf<Component>()
        lines +=
            Component.text(
                text(continent.title),
                if (continent == picked) GOLD else NamedTextColor.WHITE,
            )
        val cities = continent.zones.map { it.city }.distinct()
        cities.chunked(CITIES_PER_LINE).forEachIndexed { i, chunk ->
            val prefix = if (i == 0) "${text(SettingsMessage.REGION_MENU_DATACENTRES)} " else ""
            lines += Component.text(prefix + chunk.joinToString(", "), NamedTextColor.GRAY)
        }
        if (continent.code in live) {
            lines += Component.text(text(SettingsMessage.REGION_MENU_ONLINE), NamedTextColor.GREEN)
        }
        lines +=
            when {
                continent == picked ->
                    Component.text(text(SettingsMessage.REGION_MENU_PICKED), GOLD)
                continent.pickable ->
                    Component.text(text(SettingsMessage.REGION_MENU_PICK), NamedTextColor.YELLOW)
                else ->
                    Component.text(
                        text(SettingsMessage.REGION_MENU_UNAVAILABLE),
                        NamedTextColor.DARK_GRAY,
                    )
            }
        if (continent.pickable && continent != picked) lines += note(text)
        while (lines.size * TOOLTIP_LINE < sprite.height) lines += Component.empty()

        val card = Component.text()
        lines.forEachIndexed { i, line ->
            if (i > 0) card.append(Component.newline())
            if (i == 0) {
                card.append(
                    Component.text(sprite.glyph(0).toString())
                        .font(MenuFonts.ART)
                        .color(NamedTextColor.WHITE)
                        .shadowColor(ShadowColor.none())
                )
                card.append(space(indent - sprite.advance))
            } else {
                card.append(space(indent))
            }
            card.append(line)
        }
        return card.build()
    }

    private fun space(px: Int): Component = Component.text(MenuFonts.move(px)).font(MenuFonts.SPACE)
}
