package gg.grounds.player.settings

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * The menu's pack files live in groundsgg/resourcepacks, the code that places them lives here.
 * These tests are what keeps the two describing the same thing.
 *
 * `./gradlew :minestom:test` writes the pack to `build/menu-pack/` with a `SHA256SUMS` beside it;
 * copy both into the resourcepacks repo after changing [MenuArt] or the shaders. With
 * `MENU_PACK_UPDATE=1` the checked-in advances table is rewritten instead of compared.
 */
class MenuPackTest {

    @Test
    fun `the advances table matches the client jar`() {
        val path = Path.of("src/main/resources/gg/grounds/player/settings/advances.txt")
        val expected = MenuPack.advancesTable()
        if (System.getenv("MENU_PACK_UPDATE") == "1") Files.writeString(path, expected)
        assertEquals(expected, Files.readString(path), "regenerate with MENU_PACK_UPDATE=1")
    }

    @Test
    fun `the title advances table matches the client jar`() {
        val path = Path.of("src/main/resources/gg/grounds/player/settings/title-advances.txt")
        val expected = MenuPack.titleAdvancesTable()
        if (System.getenv("MENU_PACK_UPDATE") == "1") Files.writeString(path, expected)
        assertEquals(expected, Files.readString(path), "regenerate with MENU_PACK_UPDATE=1")
    }

    @Test
    fun `every sprite reaches its right edge, so its advance is width + 1`() {
        for (sprite in MenuArt.sprites) {
            val image = MenuPack.image(sprite)
            val rightmost =
                (0 until image.width).last { x ->
                    (0 until image.height).any { y -> image.getRGB(x, y) ushr 24 != 0 }
                }
            assertEquals(sprite.advance, rightmost + 2, sprite.name)
        }
    }

    /** grounds-global caps each piece of platform artwork at 4 KiB (`MAX_ARTWORK_BYTES`). */
    @Test
    fun `writes the pack, every sprite within the PackSet's artwork limit`() {
        val out = Path.of("build/menu-pack")
        val sums = StringBuilder()
        for ((path, bytes) in MenuPack.files().toSortedMap()) {
            if (path.endsWith(".png"))
                assertTrue(bytes.size <= 4096, "$path is ${bytes.size} bytes")
            val file = out.resolve(path)
            Files.createDirectories(file.parent)
            Files.write(file, bytes)
            val sha =
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                    "%02x".format(it)
                }
            sums.append("$sha  $path (${bytes.size} bytes)\n")
        }
        Files.writeString(out.resolve("SHA256SUMS"), sums)
    }
}
