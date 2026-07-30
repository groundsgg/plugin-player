package gg.grounds.locale

import java.util.Locale

/**
 * The languages `/lang` will accept — the ones the network's plugins actually ship bundles for. A
 * tag outside this set is rejected by the command rather than stored, so a player cannot pick a
 * language that would only ever render as the English fallback.
 *
 * English is included on purpose: it lets a player on a German client override back to English,
 * which resolves to the untranslated source bundle.
 *
 * Ordered (LinkedHashMap) so the command lists them the same way every time.
 */
object SupportedLanguages {
    val ALL: Map<String, Locale> =
        linkedMapOf(
            "en" to Locale.ENGLISH,
            "de" to Locale.GERMAN,
            "fr" to Locale.FRENCH,
            "es" to Locale.forLanguageTag("es"),
        )

    /** The [Locale] for a supported tag (case-insensitive), or null if it is not one we ship. */
    fun parse(tag: String): Locale? = ALL[tag.lowercase()]
}
