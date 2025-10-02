package de.hinundhergestellt.jhuh.vendors.shopify.taxonomy

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.hinundhergestellt.jhuh.core.loadTextResource
import java.awt.Color
import kotlin.math.sqrt

object ShopifyColorTaxonomyProvider {

    val values = ShopifyTaxonomyProvider.taxonomy.asSequence()
        .filter { it.handle.startsWith("color__") }
        .map { it.toShopifyColorTaxonomyValue() }
        .toList()

    fun findByColor(color: Color): String {
        val value = values.asSequence()
            .filter { it.color != null }
            .minByOrNull { distance(it.color!!, color) }!!
        return "gid://shopify/TaxonomyValue/${value.id}"
    }
}

data class ShopifyColorTaxonomy(
    val id: Int,
    val name: String,
    val color: Color?
)

private fun ShopifyTaxonomyValue.toShopifyColorTaxonomyValue(): ShopifyColorTaxonomy {
    val colorKey = handle.substringAfter("color__")
    val cssKey = normalizeToCssKeyword(handle)
    val color = cssColorNames[cssKey]?.toColor()
    return ShopifyColorTaxonomy(id, colorKey, color)
}

private val cssColorNames = jacksonObjectMapper().readValue<Map<String, String>>(loadTextResource { "/css-color-names.json" })

/**
 * Einfache Heuristik, um Shopify-Handles in CSS-Namen zu mappen.
 * Beispiele:
 *  - color__light_blue  -> lightblue
 *  - color__dark-grey   -> darkgray
 *  - color__navy_blue   -> navy
 *  - color__fuschia     -> fuchsia (Tippfehler)
 */
private fun normalizeToCssKeyword(raw: String): String? {
    val base = raw.lowercase()
        .replace("color__", "")
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replace(Regex("\\s+"), " ")

    // eindeutige Ausnahmen
    if (base in listOf("multicolor", "multi color", "rainbow", "various", "assorted", "clear"))
        return null

    // häufige Tippfehler/Aliasse
    val fixes = mapOf(
        "fuschia" to "fuchsia",
        "grey" to "gray",
        "light grey" to "lightgray",
        "dark grey" to "darkgray",
        "navy blue" to "navy",
        "aqua blue" to "aqua",
        "sky blue" to "skyblue",
        "baby blue" to "lightblue",
        "rose gold" to "gold" // lieber neutral auf 'gold' mappen
    )
    fixes[base]?.let { return it }

    // Versuche zusammengesetzte "light|dark + name" zusammenzuziehen
    val tokens = base.split(' ')
    if (tokens.size == 2 && tokens[0] in listOf("light", "dark", "deep")) {
        val candidate = (tokens[0] + tokens[1])
        if (candidate in cssColorNames) return candidate
    }

    // ohne Leerzeichen probieren
    val noSpace = base.replace(" ", "")
    if (noSpace in cssColorNames) return noSpace

    // direkter Treffer?
    if (base in cssColorNames) return base

    // kein Mapping gefunden
    return null
}

private fun String.toColor(): Color {
    val clean = removePrefix("#")
    val r = clean.substring(0, 2).toInt(16)
    val g = clean.substring(2, 4).toInt(16)
    val b = clean.substring(4, 6).toInt(16)
    return Color(r, g, b)
}

private fun distance(a: Color, b: Color): Double {
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    return sqrt((dr * dr + dg * dg + db * db).toDouble())
}