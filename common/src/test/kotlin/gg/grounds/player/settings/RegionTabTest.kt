package gg.grounds.player.settings

import gg.grounds.i18n.Translations
import gg.grounds.player.settings.SettingsMenu.Tab
import gg.grounds.player.settings.WorldMap.Continent
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test

class RegionTabTest {

    private val messages =
        Translations.forBundle(
            "gg.grounds.player.settings.messages",
            RegionTab::class.java.classLoader,
        )

    private val texts =
        listOf("en", "de", "fr", "es").associateWith {
            SettingsMenu.textFor(messages, Locale.forLanguageTag(it))
        }
    private val english = texts.getValue("en")

    private fun layout(
        region: String?,
        live: Set<String> = setOf("eu"),
        text: (SettingsMessage) -> String = english,
    ) = SettingsMenu.layout(SettingsMenu.State(0, region, true, true, Tab.REGION, live), text)

    private val stored = listOf(null, "xx") + Continent.entries.map { it.code }

    private val variants = texts.values.flatMap { text -> stored.map { layout(it, text = text) } }

    private val canvas = layout(null)

    /** The canvas pixel of a place on the map. */
    private fun at(lat: Double, lon: Double): Pair<Int, Int> =
        WorldMap.project(lat, lon).let { (x, y) -> SettingsMenu.X + x to RegionTab.MAP_Y + y }

    @Test
    fun `the map is the rasterised world, continents where they belong`() {
        assertEquals(WorldMap.WIDTH, WorldMap.rows.first().length)
        val continentAt = { lat: Double, lon: Double ->
            WorldMap.project(lat, lon).let { (x, y) -> WorldMap.at(x, y) }
        }
        assertEquals(Continent.EUROPE, continentAt(48.0, 10.0)) // southern Germany
        assertEquals(Continent.NORTH_AMERICA, continentAt(40.0, -100.0))
        assertEquals(Continent.SOUTH_AMERICA, continentAt(-10.0, -55.0))
        assertEquals(Continent.AFRICA, continentAt(5.0, 20.0))
        assertEquals(Continent.ASIA, continentAt(60.0, 100.0)) // Siberia, not Europe
        assertEquals(Continent.OCEANIA, continentAt(-25.0, 135.0))
        assertNull(continentAt(30.0, -40.0)) // mid-Atlantic
    }

    @Test
    fun `every continent with a datacentre can be picked`() {
        assertEquals(
            Continent.entries.filter { it.zones.isNotEmpty() }.toSet(),
            Continent.entries.filter { it.pickable }.toSet(),
        )
        assertEquals(
            setOf("na", "sa", "eu", "af", "as", "oc"),
            RegionTab.PICKS.values.map { it.code }.toSet(),
        )
        assertEquals(33, WorldMap.ZONES.size)
        assertEquals(WorldMap.ZONES.size, WorldMap.ZONES.map { it.code }.distinct().size)
    }

    @Test
    fun `clicking a continent picks it, South America and Africa too`() {
        val clickAt = { lat: Double, lon: Double ->
            at(lat, lon).let { (x, y) -> ClientSim.clickAt(canvas, x, y) }
        }
        assertEquals(RegionTab.pick(Continent.EUROPE), clickAt(48.0, 10.0))
        assertEquals(RegionTab.pick(Continent.NORTH_AMERICA), clickAt(40.0, -100.0))
        assertEquals(RegionTab.pick(Continent.OCEANIA), clickAt(-25.0, 135.0))
        assertEquals(RegionTab.pick(Continent.SOUTH_AMERICA), clickAt(-10.0, -55.0))
        assertEquals(RegionTab.pick(Continent.AFRICA), clickAt(5.0, 20.0))
    }

    /**
     * The shipped pack numbers its glyphs in declaration order. Every sprite it already has keeps
     * its index; the picked South America and Africa come after all of them.
     */
    @Test
    fun `newly pickable continents append glyphs instead of moving the shipped ones`() {
        val picked = MenuArt.landsPicked
        val first = WorldMap.Continent.FIRST_PICKS.map { picked.getValue(it).index }
        val later =
            listOf(Continent.SOUTH_AMERICA, Continent.AFRICA).map { picked.getValue(it).index }
        val cards = MenuArt.cards.values.maxOf { it.index }
        assertTrue(first.all { it < cards }, "first picks moved: $first")
        assertEquals(listOf(cards + 1, cards + 2), later.sorted())
        assertEquals(cards + 3, MenuArt.sprites.size)
    }

    @Test
    fun `the tooltip names the datacentres and says which region is live`() {
        val (x, y) = at(48.0, 10.0)
        val tip =
            PlainTextComponentSerializer.plainText().serialize(ClientSim.hoverAt(canvas, x, y)!!)
        assertTrue(tip.filterNot { it in '\uE000'..'\uF8FF' }.startsWith("Europe"), tip)
        assertTrue("Frankfurt" in tip && "Warsaw" in tip, tip)
        assertTrue("Servers online now" in tip, tip)
    }

    @Test
    fun `the picked continent no longer clicks, and automatic offers itself back`() {
        val picked = layout("eu", emptySet())
        val (x, y) = at(48.0, 10.0)
        assertNull(ClientSim.clickAt(picked, x, y))
        val rowX = SettingsMenu.X + 20
        assertEquals(
            RegionTab.AUTOMATIC,
            ClientSim.clickAt(picked, rowX, RegionTab.ROW_Y + 13),
            "automatic row clicks while a region is set",
        )
        assertNull(
            ClientSim.clickAt(canvas, rowX, RegionTab.ROW_Y + 13),
            "and not while it is already automatic",
        )
    }

    @Test
    fun `the region tab is on the rail, whichever tab is open`() {
        for (tab in Tab.entries) {
            val state = SettingsMenu.State(0, null, true, true, tab)
            val hits = SettingsMenu.layout(state, english).elements.filterIsInstance<Element.Hit>()
            assertNotNull(hits.firstOrNull { it.action == Tab.REGION.key }, "$tab")
            assertEquals(
                tab == Tab.REGION,
                hits.any { it.action == RegionTab.pick(Continent.EUROPE) },
                "$tab: the map belongs to the region tab only",
            )
        }
    }

    @Test
    fun `lines are well formed for every state`() = variants.forEach(ClientSim::assertWellFormed)

    @Test fun `every hit is exact`() = variants.forEach(ClientSim::assertHitsExact)

    @Test fun `no label is painted over`() = variants.forEach(ClientSim::assertNoCoveredText)

    @Test
    fun `the status row's text stays clear of the toggle, in any language`() {
        val toggle = SettingsMenu.X + MenuArt.ROW_W - 32
        for (c in variants) {
            val labels = c.elements.filterIsInstance<Element.Label>()
            val (label, value) = labels.filter { it.y == RegionTab.ROW_Y + 4 }.sortedBy { it.x }
            assertTrue(
                label.x + label.width + 4 <= value.x,
                "'${label.text}' runs into '${value.text}'",
            )
            for (line in listOf(value, labels.single { it.y == RegionTab.ROW_Y + 15 })) {
                assertTrue(line.x + line.width + 4 <= toggle, "'${line.text}' runs into the toggle")
            }
        }
    }

    @Test
    fun `the hint beside the heading leaves the heading room, in any language`() {
        for (c in variants) {
            val (heading, hint) =
                c.elements.filterIsInstance<Element.Label>().filter { it.y == 9 }.sortedBy { it.x }
            // the hand icon sits 12 px left of the hint
            assertTrue(heading.x + heading.width + 16 <= hint.x - 12, "'${hint.text}' is too long")
        }
    }

    @Test
    fun `name tags stay on the map, clear of each other and of the datacentre dots`() {
        val dots =
            WorldMap.ZONES.map { WorldMap.project(it.lat, it.lon) }
                .distinct()
                .map { (zx, zy) -> listOf(SettingsMenu.X + zx - 1, RegionTab.MAP_Y + zy - 1, 3, 3) }
        for ((language, text) in texts) {
            val tags =
                Continent.entries.map { continent ->
                    RegionTab.tagBox(SettingsMenu.X, continent, text(continent.title)).let {
                        (left, top, width) ->
                        listOf(left, top, width, MenuArt.PILL_H)
                    }
                }
            val overlaps = { a: List<Int>, b: List<Int> ->
                a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3]
            }
            tags.forEachIndexed { i, tag ->
                assertTrue(
                    tag[0] >= SettingsMenu.X && tag[0] + tag[2] <= SettingsMenu.X + WorldMap.WIDTH,
                    "$language: tag $i leaves the map",
                )
                assertTrue(dots.none { overlaps(tag, it) }, "$language: tag $i covers a datacentre")
                tags.drop(i + 1).forEach {
                    assertFalse(overlaps(tag, it), "$language: tags overlap")
                }
            }
        }
    }

    @Test
    fun `the hover card shows the silhouette and gives it room`() {
        for (continent in Continent.entries) {
            val card = RegionTab.card(continent, null, emptySet(), english)
            val glyph = MenuArt.cards.getValue(continent).glyph(0).toString()
            val plain = PlainTextComponentSerializer.plainText().serialize(card)
            assertTrue(plain.startsWith(glyph), "${continent.name}: no silhouette")
            val lines = plain.count { it == '\n' } + 1
            assertTrue(
                lines * 10 >= MenuArt.cards.getValue(continent).height,
                "${continent.name}: $lines lines cannot hold the silhouette",
            )
        }
    }

    @Test
    fun `the whole settings menu fits a 1080p screen at GUI scale 4`() {
        // 480 x 270 GUI px, minus the dialog's margins, title bar and button row
        assertTrue(canvas.width <= 480 - 2 * 16, "${canvas.width} px wide")
        assertTrue(MenuArt.TAB_H <= 270 - 75)
    }

    @Test
    fun `every region text exists in every language`() {
        for ((language, text) in texts) {
            for (key in SettingsMessage.entries.filter { it.id.startsWith("region.menu.") }) {
                assertFalse(text(key).isBlank(), "$language: ${key.id} is empty")
            }
        }
    }

    /** `build/region-preview.png`: the art as placed (no text), for eyeballing the tab. */
    @Test
    fun `writes a preview`() {
        // 3x, with labels in an approximate font: enough to judge the layout, not the glyphs
        val s = 3
        val c = layout("eu", emptySet(), texts.getValue("de"))
        val image = BufferedImage(c.width * s, MenuArt.TAB_H * s, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        // Labels need a system font, which a CI runner may not have ("Fontconfig head is null");
        // without one the preview shows the art alone.
        val withLabels =
            runCatching {
                    g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 8 * s)
                    g.fontMetrics
                }
                .isSuccess
        for (e in c.elements.sortedBy { if (it is Element.Label) 1 else 0 }) {
            when (e) {
                is Element.Art -> {
                    val sprite = MenuPack.image(e.sprite)
                    g.drawImage(sprite, e.x * s, e.y * s, sprite.width * s, sprite.height * s, null)
                }
                is Element.Label ->
                    if (withLabels) {
                        g.color = java.awt.Color(e.color.value())
                        g.drawString(e.text, e.x * s, (e.y + 7) * s)
                    }
                is Element.Hit -> Unit
            }
        }
        g.dispose()
        val out = Path.of("build/region-preview.png")
        Files.createDirectories(out.parent)
        ImageIO.write(image, "png", out.toFile())
    }
}
