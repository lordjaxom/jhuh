package de.hinundhergestellt.jhuh.vendors.shopify.taxonomy

import java.awt.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object ShopifyColorTaxonomyProvider {

    val colors = ShopifyTaxonomyProvider.taxonomy.asSequence()
        .filter { it.handle.startsWith("color__") }
        .map { it.toShopifyColorTaxonomy() }
        .associateBy { it.id }

    fun findNearestByColor(color: Color) =
        colors.values.asSequence()
            .filter { it.color != null }
            .minByOrNull { hslDistance(it.color!!, color) }!!
}

data class ShopifyColorTaxonomy(
    val id: String,
    val name: String,
    val color: Color?
)

private fun RawTaxonomy.toShopifyColorTaxonomy() =
    ShopifyColorTaxonomy("gid://shopify/TaxonomyValue/$key", name, SHOPIFY_COLORS[name])

private val SHOPIFY_COLORS = mapOf(
    "Black" to Color(0x00, 0x00, 0x00),
    "Blue" to Color(0x00, 0x00, 0xFF),
    "White" to Color(0xFF, 0xFF, 0xFF),
//    "Gold" to Color(0xFF, 0xD7, 0x00),
//    "Silver" to Color(0xC0, 0xC0, 0xC0),
    "Beige" to Color(0xF5, 0xF5, 0xDC),
    "Brown" to Color(0x8B, 0x45, 0x13),
    "Gray" to Color(0x80, 0x80, 0x80),
    "Green" to Color(0x00, 0x80, 0x00),
    "Orange" to Color(0xFF, 0xA5, 0x00),
    "Pink" to Color(0xFF, 0xC0, 0xCB),
    "Purple" to Color(0x80, 0x00, 0x80),
    "Red" to Color(0xFF, 0x00, 0x00),
    "Yellow" to Color(0xFF, 0xFF, 0x00),
    "Navy" to Color(0x00, 0x00, 0x80),
//    "Rose gold" to Color(0xB7, 0x6E, 0x79),
//    "Bronze" to Color(0xCD, 0x7F, 0x32),
)

/** einfache euklidische Distanz im RGB-Raum */
fun colorDistance(c1: Color, c2: Color): Double {
    val dr = (c1.red - c2.red).toDouble()
    val dg = (c1.green - c2.green).toDouble()
    val db = (c1.blue - c2.blue).toDouble()
    return sqrt(dr.pow(2) + dg.pow(2) + db.pow(2))
}

/** RGB (0..255) -> HSL: H in [0,360), S/L in [0,1] */
fun Color.toHsl(): Triple<Double, Double, Double> {
    val r = red / 255.0; val g = green / 255.0; val b = blue / 255.0
    val max = max(r, max(g, b)); val min = min(r, min(g, b))
    val l = (max + min) / 2.0
    val d = max - min
    if (d == 0.0) return Triple(0.0, 0.0, l)
    val s = if (l > 0.5) d / (2.0 - max - min) else d / (max + min)
    val h = when (max) {
        r -> (g - b) / d + (if (g < b) 6 else 0)
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    } * 60.0
    return Triple(h % 360.0, s, l)
}

/** zirkuläre Hue-Distanz in Grad */
private fun hueDelta(h1: Double, h2: Double): Double {
    val d = abs(h1 - h2) % 360.0
    return min(d, 360.0 - d)
}

/**
 * HSL-Distanz:
 * - Hue stark gewichten, aber abhängig von Sättigung (bei grauen/weißen/schwarzen Tönen ist Hue irrelevant)
 * - S moderat, L leichter gewichtet
 */
fun hslDistance(c1: Color, c2: Color): Double {
    val (h1, s1, l1) = c1.toHsl()
    val (h2, s2, l2) = c2.toHsl()
    val sAvg = (s1 + s2) / 2.0
    val wH = 2.5 * sAvg              // Hue zählt nur, wenn satt
    val wS = 1.2
    val wL = 0.7
    val dh = hueDelta(h1, h2) / 180.0        // normiert ~[0..1]
    val ds = abs(s1 - s2)
    val dl = abs(l1 - l2)
    return sqrt(wH*dh*dh + wS*ds*ds + wL*dl*dl)
}
