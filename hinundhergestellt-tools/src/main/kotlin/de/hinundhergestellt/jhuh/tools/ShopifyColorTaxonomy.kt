package de.hinundhergestellt.jhuh.tools

import java.awt.Color
import kotlin.math.sqrt

private val colorTaxonomyValues = listOf(
    Triple(1, "black", Color(0, 0, 0)),
    Triple(2, "blue", Color(0, 0, 255)),
    Triple(3, "white", Color(255, 255, 255)),
    Triple(4, "gold", Color(255, 215, 0)),
    Triple(5, "silver", Color(192, 192, 192)),
    Triple(6, "beige", Color(245, 245, 220)),
    Triple(7, "brown", Color(165, 42, 42)),
    Triple(8, "gray", Color(128, 128, 128)),
    Triple(9, "green", Color(0, 128, 0)),
    Triple(10, "orange", Color(255, 165, 0)),
    Triple(11, "pink", Color(255, 192, 203)),
    Triple(12, "purple", Color(128, 0, 128)),
    Triple(13, "red", Color(255, 0, 0)),
    Triple(14, "yellow", Color(255, 255, 0)),
    Triple(15, "navy", Color(0, 0, 128)),
    Triple(16, "rose-gold", Color(255, 215, 0)),
    Triple(17, "clear", null),
    Triple(657, "bronze", Color(191, 137, 112)),
    Triple(2865, "multicolor", null)
)

object ShopifyColorTaxonomy {

    fun findByColor(color: Color): String {
        val value = colorTaxonomyValues.asSequence()
            .filter { it.third != null }
            .minByOrNull { distance(it.third!!, color) }!!
        return "gid://shopify/TaxonomyValue/${value.first}"
    }

    fun distance(a: Color, b: Color): Double {
        val dr = a.red - b.red
        val dg = a.green - b.green
        val db = a.blue - b.blue
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }
}