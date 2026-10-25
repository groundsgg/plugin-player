package gg.grounds.player.settings

import com.google.gson.JsonParser
import java.awt.image.BufferedImage
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * Vanilla's bitmap text providers and their glyph advances, read from the official 26.3 client jar.
 * Test-only: the server reads the checked-in advances table instead.
 *
 * The jar is never redistributed: only its `chars` grids end up in the pack, pointing at vanilla's
 * own textures, which the client already has.
 */
internal object VanillaFonts {
    /** One `bitmap` provider of `minecraft:include/default`, in vanilla's order. */
    data class Provider(val file: String, val height: Int, val ascent: Int, val chars: List<String>)

    private const val CLIENT_URL =
        "https://piston-data.mojang.com/v1/objects/e877b6a07acd633fb3bb475002175cec036e7b87/client.jar"
    private const val CLIENT_SHA1 = "e877b6a07acd633fb3bb475002175cec036e7b87"

    private val loaded by lazy { load(clientJar()) }

    /** Each bitmap provider's texture, by its `file`. */
    private val images = HashMap<String, BufferedImage>()

    /** Vanilla's bitmap providers, first match wins, exactly as the client resolves them. */
    val providers: List<Provider>
        get() = loaded.first

    /** Advance of [codepoint] in vanilla's default font, or null if no bitmap provider has it. */
    fun advance(codepoint: Int): Int? =
        when (codepoint) {
            ' '.code -> 4 // minecraft:include/space
            else -> loaded.second[codepoint]
        }

    private fun load(jar: Path): Pair<List<Provider>, Map<Int, Int>> =
        ZipFile(jar.toFile()).use { zip ->
            fun read(path: String): InputStream =
                zip.getInputStream(zip.getEntry(path) ?: error("$path missing from the client jar"))

            val definition =
                read("assets/minecraft/font/include/default.json").use {
                    JsonParser.parseReader(it.reader()).asJsonObject
                }
            val providers = mutableListOf<Provider>()
            val advances = HashMap<Int, Int>()
            for (element in definition.getAsJsonArray("providers")) {
                val json = element.asJsonObject
                if (json["type"].asString != "bitmap") continue
                val provider =
                    Provider(
                        file = json["file"].asString,
                        height = json["height"]?.asInt ?: 8,
                        ascent = json["ascent"].asInt,
                        chars = json.getAsJsonArray("chars").map { it.asString },
                    )
                providers += provider
                val texture = "assets/" + provider.file.replace(":", "/textures/")
                val image = read(texture).use { ImageIO.read(it) }
                images[provider.file] = image
                measure(provider, image, advances)
            }
            providers to advances
        }

    /**
     * Advances of [provider] drawn at [height] instead of its own, measured the client's way, for a
     * copy of it scaled up (the title font).
     */
    fun advancesAt(provider: Provider, height: Int): Map<Int, Int> {
        loaded
        val into = HashMap<Int, Int>()
        measure(provider.copy(height = height), images.getValue(provider.file), into)
        return into
    }

    /**
     * The client's own rule: a glyph is as wide as its rightmost column holding any visible pixel,
     * scaled to the provider's height, plus one pixel of spacing. The first provider to define a
     * codepoint keeps it, as in the client.
     */
    private fun measure(provider: Provider, image: BufferedImage, into: MutableMap<Int, Int>) {
        val rows = provider.chars.map { it.codePoints().toArray() }
        val cellW = image.width / rows[0].size
        val cellH = image.height / rows.size
        val scale = provider.height.toFloat() / cellH
        rows.forEachIndexed { row, codepoints ->
            codepoints.forEachIndexed { col, cp ->
                if (cp == 0 || cp in into) return@forEachIndexed
                var width = 0
                for (x in cellW - 1 downTo 0) {
                    val visible =
                        (0 until cellH).any { y ->
                            image.getRGB(col * cellW + x, row * cellH + y) ushr 24 != 0
                        }
                    if (visible) {
                        width = x + 1
                        break
                    }
                }
                into[cp] = (0.5f + width * scale).toInt() + 1
            }
        }
    }

    /** The pinned, SHA-1-verified client jar: `MENU_CLIENT_JAR`, the local cache, or a download. */
    private fun clientJar(): Path {
        System.getenv("MENU_CLIENT_JAR")?.let {
            return verified(Path.of(it))
        }
        val cached =
            Path.of(System.getProperty("user.home"), ".cache", "grounds-menu", "client-26.3.jar")
        if (Files.isRegularFile(cached)) return verified(cached)
        Files.createDirectories(cached.parent)
        val partial = cached.resolveSibling("client-26.3.jar.part")
        URI(CLIENT_URL).toURL().openStream().use {
            Files.copy(it, partial, StandardCopyOption.REPLACE_EXISTING)
        }
        verified(partial)
        return Files.move(partial, cached, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun verified(jar: Path): Path {
        val sha1 =
            MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(jar)).joinToString("") {
                "%02x".format(it)
            }
        require(sha1 == CLIENT_SHA1) {
            "$jar is not the 26.3 client jar (sha1 $sha1, expected $CLIENT_SHA1)"
        }
        return jar
    }
}
