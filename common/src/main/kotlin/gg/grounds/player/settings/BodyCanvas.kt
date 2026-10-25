package gg.grounds.player.settings

import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.api.BinaryTagHolder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor

/** Something placed on the canvas, in panel pixels (origin top-left). */
internal sealed interface Element {
    val y: Int

    data class Art(val sprite: MenuSprite, val x: Int, override val y: Int) : Element

    data class Label(
        val text: String,
        val x: Int,
        override val y: Int,
        val color: TextColor = NamedTextColor.WHITE,
        val shadow: Boolean = true,
        /** Drawn twice the size in [MenuFonts.TITLE]; only on a line's top (y a multiple of 9). */
        val title: Boolean = false,
    ) : Element {
        val width: Int
            get() = if (title) MenuFonts.titleWidth(text) else MenuFonts.textWidth(text)
    }

    /**
     * An invisible rectangle with a click [action] and/or a [hover] tooltip. Draw its look with
     * [Art]/[Label].
     */
    data class Hit(
        val x: Int,
        override val y: Int,
        val w: Int,
        val h: Int,
        val action: Key?,
        val hover: Component? = null,
    ) : Element
}

/**
 * Paints [Element]s into one dialog body component.
 *
 * The body is lines of text [MenuFonts.LINE_HEIGHT] apart, each centred. Every line is built to
 * advance exactly [width] px net, so centring puts every line's x = 0 on the same column, and
 * within a line the cursor is steered with [MenuFonts.move].
 *
 * An element is drawn on the line its top falls into, dropped by the remaining 0..8 px via the dy
 * variant of its font. Hits are different. The 26.3 client tests a click against every glyph of a
 * line whose *visible* glyphs' bounds contain the point, and the last glyph with a click event
 * under it wins. So each line *starts* with a strictly left-to-right run covering
 * [0, width): invisible bitmap strips ([MenuArt.hits]) where a hit overlaps that line's 9 px band,
 * plain space elsewhere. The strips both make the band count as part of the line and carry the
 * click; the art drawn after them has no click and never takes one.
 *
 * Hits therefore belong on the 9 px grid (y and h multiples of 9): two hits sharing a line cannot
 * be told apart, and the line goes to whichever covers more of it.
 */
internal class BodyCanvas(val width: Int, private val height: Int) {
    internal val elements = mutableListOf<Element>()

    fun art(sprite: MenuSprite, x: Int, y: Int) = apply { elements += Element.Art(sprite, x, y) }

    fun label(
        text: String,
        x: Int,
        y: Int,
        color: TextColor = NamedTextColor.WHITE,
        shadow: Boolean = true,
        title: Boolean = false,
    ) = apply { elements += Element.Label(text, x, y, color, shadow, title) }

    fun hit(x: Int, y: Int, w: Int, h: Int, action: Key?, hover: Component? = null) = apply {
        elements += Element.Hit(x, y, w, h, action, hover)
    }

    val lineCount: Int
        get() = (height + MenuFonts.LINE_HEIGHT - 1) / MenuFonts.LINE_HEIGHT

    /**
     * [clickKey] turns each logical action into the key actually sent, e.g. a [ClickTokens] token.
     */
    fun render(clickKey: (Key) -> Key = { it }): Component {
        val root = Component.text()
        for (line in 0 until lineCount) {
            if (line > 0) root.append(Component.newline())
            renderLine(line, clickKey).forEach(root::append)
        }
        return root.build()
    }

    private fun renderLine(line: Int, clickKey: (Key) -> Key): List<Component> {
        val out = mutableListOf<Component>()
        val top = line * MenuFonts.LINE_HEIGHT
        val bottom = top + MenuFonts.LINE_HEIGHT

        // 1. hit spans, strictly left to right, covering the full width. Where two hits share
        // this line, it goes to whichever covers more of it and the other drops out where their
        // x ranges collide.
        var cursor = 0
        val claimed = mutableListOf<Element.Hit>()
        elements
            .filterIsInstance<Element.Hit>()
            .filter { it.y < bottom && it.y + it.h > top }
            .sortedByDescending { minOf(bottom, it.y + it.h) - maxOf(top, it.y) }
            .forEach { hit ->
                if (claimed.none { it.x < hit.x + hit.w && hit.x < it.x + it.w }) claimed += hit
            }
        for (hit in claimed.sortedBy { it.x }) {
            val to = minOf(hit.x + hit.w, width)
            if (hit.x > cursor) out += space(hit.x - cursor)
            out += span(to - hit.x, hit, clickKey)
            cursor = to
        }
        if (cursor < width) out += space(width - cursor)
        out += space(-width)
        cursor = 0

        // 2. visuals anchored on this line, art under text
        val anchored = elements.filter { it !is Element.Hit && it.y in top until bottom }
        for (e in anchored.sortedBy { if (it is Element.Art) 0 else 1 }) {
            val dy = e.y - top
            when (e) {
                is Element.Art -> {
                    out += space(e.x - cursor)
                    out +=
                        Component.text(e.sprite.glyph(dy).toString())
                            .font(MenuFonts.ART)
                            .color(NamedTextColor.WHITE)
                            .shadowColor(ShadowColor.none())
                    cursor = e.x + e.sprite.advance
                }
                is Element.Label -> {
                    out += space(e.x - cursor)
                    require(!e.title || dy == 0) {
                        "a title sits on a line's top, not ${dy}px into it"
                    }
                    val font = if (e.title) MenuFonts.TITLE else MenuFonts.text(dy)
                    val label = Component.text(e.text).font(font).color(e.color)
                    out += if (e.shadow) label else label.shadowColor(ShadowColor.none())
                    cursor = e.x + e.width
                }
                is Element.Hit -> error("unreachable")
            }
        }

        // 3. end the line exactly `width` px from where it started
        out += space(width - cursor)
        return out.filter { it !is TextComponent || it.content().isNotEmpty() }
    }

    private fun space(px: Int): Component = Component.text(MenuFonts.move(px)).font(MenuFonts.SPACE)

    /**
     * A hit's span: [MenuArt.hits] strips adding up to [px], each followed by a 1 px step back (a
     * bitmap glyph advances one past its art). The strips carry the click and the tooltip; the step
     * back carries nothing.
     */
    private fun span(px: Int, hit: Element.Hit, clickKey: (Key) -> Key): List<Component> {
        // Minestom cannot encode a custom click without a payload, so send an empty compound
        val click =
            hit.action?.let {
                ClickEvent.custom(clickKey(it), BinaryTagHolder.binaryTagHolder("{}"))
            }
        val hover = hit.hover?.let { HoverEvent.showText(it) }
        return MenuFonts.powers
            .filter { px and (1 shl it) != 0 }
            .flatMap { bit ->
                val strip =
                    Component.text(MenuArt.hits[bit].glyph(0).toString())
                        .font(MenuFonts.ART)
                        .color(NamedTextColor.WHITE)
                        .shadowColor(ShadowColor.none())
                        .clickEvent(click)
                        .hoverEvent(hover)
                listOf(strip, space(-1))
            }
    }
}
