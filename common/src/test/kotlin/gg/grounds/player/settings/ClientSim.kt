package gg.grounds.player.settings

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent

/**
 * Lays a body out and resolves clicks and hovers the way the 26.3 client does.
 *
 * 26.3 does not walk advances any more (`ActiveTextCollector.findElementUnderCursor`). Every body
 * line is its own render state; a point counts for a line only inside that line's bounds, and the
 * bounds grow with *visible* glyphs alone (bitmap quads), never with space-font glyphs. Inside the
 * bounds every glyph is tested against its active rectangle (a bitmap glyph: its quad's top/bottom,
 * x to x + advance; a space glyph: x to x + advance over the 9 px line), visible glyphs first, then
 * the empty areas, and the last match that carries the wanted event wins.
 */
internal object ClientSim {
    /** One glyph as laid out: its advance, the font it came from and the style events on it. */
    data class Glyph(
        val advance: Int,
        val font: Key?,
        val ch: Char,
        val click: Key?,
        val hover: Component?,
    )

    private data class Area(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val visible: Boolean,
        val click: Key?,
        val hover: Component?,
    ) {
        fun contains(x: Int, y: Int) = x >= left && x < right && y >= top && y < bottom
    }

    private val spaceAdvance: Map<Char, Int> =
        MenuFonts.powers
            .flatMap {
                listOf(MenuFonts.negChar(it) to -(1 shl it), MenuFonts.posChar(it) to (1 shl it))
            }
            .toMap()

    private val spriteByGlyph: Map<Char, Pair<MenuSprite, Int>> =
        MenuArt.sprites
            .flatMap { s -> (0 until MenuFonts.LINE_HEIGHT).map { s.glyph(it) to (s to it) } }
            .toMap()

    /** Flattens the body into lines of glyphs, the way the client lays it out. */
    fun lines(body: Component): List<List<Glyph>> {
        val lines = mutableListOf(mutableListOf<Glyph>())
        fun walk(c: Component) {
            if (c is TextComponent) {
                val click = (c.clickEvent()?.payload() as? ClickEvent.Payload.Custom)?.key()
                @Suppress("UNCHECKED_CAST") val hover = c.hoverEvent()?.value() as? Component
                for (ch in c.content()) {
                    if (ch == '\n') {
                        lines += mutableListOf<Glyph>()
                        continue
                    }
                    val adv =
                        when (c.font()) {
                            MenuFonts.SPACE -> spaceAdvance.getValue(ch)
                            MenuFonts.ART -> spriteByGlyph.getValue(ch).first.advance
                            MenuFonts.TITLE -> MenuFonts.titleWidth(ch.toString())
                            else -> MenuFonts.textWidth(ch.toString())
                        }
                    lines.last() += Glyph(adv, c.font(), ch, click, hover)
                }
            }
            c.children().forEach(::walk)
        }
        walk(body)
        return lines
    }

    /** Every glyph of every line as the client measures it, in canvas pixels. */
    private fun areas(canvas: BodyCanvas): List<List<Area>> =
        lines(canvas.render()).mapIndexed { index, line ->
            val top = index * MenuFonts.LINE_HEIGHT
            var x = 0
            line.map { g ->
                val area =
                    when {
                        g.font == MenuFonts.SPACE ->
                            Area(x, top, x + g.advance, top + 9, false, g.click, g.hover)
                        g.font == MenuFonts.ART -> {
                            val (sprite, dy) = spriteByGlyph.getValue(g.ch)
                            Area(
                                x,
                                top + dy,
                                x + g.advance,
                                top + dy + sprite.height,
                                true,
                                g.click,
                                g.hover,
                            )
                        }
                        g.font == MenuFonts.TITLE ->
                            Area(x, top, x + g.advance, top + 16, g.ch != ' ', g.click, g.hover)
                        else -> {
                            val dy = g.font?.value()?.substringAfterLast('_')?.toIntOrNull() ?: 0
                            Area(
                                x,
                                top + dy,
                                x + g.advance,
                                top + dy + 8,
                                g.ch != ' ',
                                g.click,
                                g.hover,
                            )
                        }
                    }
                x += g.advance
                area
            }
        }

    private fun <T : Any> find(canvas: BodyCanvas, x: Int, y: Int, pick: (Area) -> T?): T? {
        var result: T? = null
        for (line in areas(canvas)) {
            val visible = line.filter { it.visible }
            if (visible.isEmpty()) continue
            val inBounds =
                x >= visible.minOf { it.left } &&
                    x < visible.maxOf { it.right } &&
                    y >= visible.minOf { it.top } &&
                    y < visible.maxOf { it.bottom }
            if (!inBounds) continue
            for (area in visible + line.filter { !it.visible }) {
                if (area.contains(x, y)) pick(area)?.let { result = it }
            }
        }
        return result
    }

    /** The click a press at canvas pixel (x, y) sends, or null. */
    fun clickAt(canvas: BodyCanvas, x: Int, y: Int): Key? = find(canvas, x, y) { it.click }

    /** The tooltip shown with the mouse at canvas pixel (x, y), or null. */
    fun hoverAt(canvas: BodyCanvas, x: Int, y: Int): Component? = find(canvas, x, y) { it.hover }

    /**
     * Every line nets exactly the canvas width and never runs outside ±width (so nothing wraps).
     */
    fun assertWellFormed(canvas: BodyCanvas) {
        val lines = lines(canvas.render())
        assertEquals(canvas.lineCount, lines.size)
        lines.forEachIndexed { i, line ->
            assertEquals(canvas.width, line.sumOf { it.advance }, "line $i width")
            var x = 0
            for (g in line) {
                x += g.advance
                assertTrue(x in -canvas.width..canvas.width, "line $i reaches $x")
            }
        }
    }

    /**
     * Every pixel row of every hit, at its left edge, centre and right edge, resolves to that hit:
     * its click if it has one, its tooltip otherwise. A whole row must answer, not just the part
     * that happens to sit on the first of its lines.
     */
    fun assertHitsExact(canvas: BodyCanvas) {
        for (hit in canvas.elements.filterIsInstance<Element.Hit>()) {
            for (y in hit.y until hit.y + hit.h) {
                for (x in listOf(hit.x, hit.x + hit.w / 2, hit.x + hit.w - 1)) {
                    if (hit.action != null) {
                        assertEquals(hit.action, clickAt(canvas, x, y), "click at ($x, $y)")
                    }
                    if (hit.hover != null) {
                        assertEquals(hit.hover, hoverAt(canvas, x, y), "hover at ($x, $y)")
                    }
                }
            }
        }
    }

    /**
     * No label is painted over by art. Lines draw top to bottom, and a label dropped into its line
     * reaches into the next one, so art anchored on a later line would cover its lower rows.
     */
    fun assertNoCoveredText(canvas: BodyCanvas) {
        val arts = canvas.elements.filterIsInstance<Element.Art>()
        for (label in canvas.elements.filterIsInstance<Element.Label>()) {
            val line = label.y / MenuFonts.LINE_HEIGHT
            val inkBottom = label.y + if (label.title) 16 else 8
            val right = label.x + label.width
            for (art in arts) {
                val covers =
                    art.y / MenuFonts.LINE_HEIGHT > line &&
                        art.y < inkBottom &&
                        art.x < right &&
                        art.x + art.sprite.width > label.x &&
                        !art.sprite.name.startsWith("hit_")
                assertTrue(
                    !covers,
                    "'${label.text}' at y=${label.y} is covered by ${art.sprite.name}",
                )
            }
        }
    }
}
