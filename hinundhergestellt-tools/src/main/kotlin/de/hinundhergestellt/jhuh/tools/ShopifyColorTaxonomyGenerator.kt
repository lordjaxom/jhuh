package de.hinundhergestellt.jhuh.tools

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.awt.Color
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class ValueYaml(
    val id: Int? = null,
    val handle: String? = null,
    val name: String? = null
)

fun fetchShopifyValuesYaml(url: String): List<ValueYaml> {
    val client = HttpClient.newHttpClient()
    val req = HttpRequest.newBuilder(URI.create(url)).GET().build()
    val body = client.send(req, HttpResponse.BodyHandlers.ofString()).body()

    val mapper = ObjectMapper(YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // values.yml ist eine YAML-Liste
    val type = mapper.typeFactory.constructCollectionType(List::class.java, ValueYaml::class.java)
    return mapper.readValue(body, type)
}

/**
 * Map der CSS/HTML-Farbnamen -> Hex (ohne #).
 * Vollständige Keyword-Liste gemäß CSS Color (MDN); hier eine kompakte, praxistaugliche Auswahl
 * + ein paar sinnvolle Aliasse (grey→gray, light-grey→lightgray …).
 * Ergänze bei Bedarf weitere Keywords.
 */
private val cssNamedHex: Map<String, String> = mapOf(
    // Basic 16
    "black" to "000000", "silver" to "c0c0c0", "gray" to "808080", "white" to "ffffff",
    "maroon" to "800000", "red" to "ff0000", "purple" to "800080", "fuchsia" to "ff00ff",
    "green" to "008000", "lime" to "00ff00", "olive" to "808000", "yellow" to "ffff00",
    "navy" to "000080", "blue" to "0000ff", "teal" to "008080", "aqua" to "00ffff",
    // Häufig genutzte erweiterte
    "orange" to "ffa500", "brown" to "a52a2a", "beige" to "f5f5dc", "tan" to "d2b48c",
    "coral" to "ff7f50", "salmon" to "fa8072", "tomato" to "ff6347", "crimson" to "dc143c",
    "pink" to "ffc0cb", "deeppink" to "ff1493", "hotpink" to "ff69b4",
    "violet" to "ee82ee", "indigo" to "4b0082", "magenta" to "ff00ff",
    "cyan" to "00ffff", "turquoise" to "40e0d0", "lightseagreen" to "20b2aa",
    "mintcream" to "f5fffa",
    "skyblue" to "87ceeb", "lightskyblue" to "87cefa", "deepskyblue" to "00bfff",
    "dodgerblue" to "1e90ff", "royalblue" to "4169e1", "midnightblue" to "191970",
    "steelblue" to "4682b4", "slateblue" to "6a5acd",
    "lightblue" to "add8e6", "powderblue" to "b0e0e6", "cadetblue" to "5f9ea0",
    "darkblue" to "00008b", "darkslateblue" to "483d8b",
    "lightgreen" to "90ee90", "palegreen" to "98fb98", "forestgreen" to "228b22",
    "seagreen" to "2e8b57", "darkgreen" to "006400", "greenyellow" to "adff2f",
    "chartreuse" to "7fff00", "springgreen" to "00ff7f",
    "khaki" to "f0e68c", "darkkhaki" to "bdb76b", "gold" to "ffd700",
    "lightyellow" to "ffffe0", "lemonchiffon" to "fffacd",
    "lavender" to "e6e6fa", "plum" to "dda0dd", "orchid" to "da70d6",
    "sienna" to "a0522d", "peru" to "cd853f", "chocolate" to "d2691e",
    "firebrick" to "b22222", "indianred" to "cd5c5c",
    "darkred" to "8b0000", "darkorange" to "ff8c00", "darkviolet" to "9400d3",
    "darkslategray" to "2f4f4f", "slategray" to "708090", "lightslategray" to "778899",
    "lightgray" to "d3d3d3", "darkgray" to "a9a9a9", "gainsboro" to "dcdcdc",
    "whitesmoke" to "f5f5f5", "aliceblue" to "f0f8ff",
    // Aliasse/Schreibvarianten
    "grey" to "808080", "lightgrey" to "d3d3d3", "darkgrey" to "a9a9a9",
    "navyblue" to "000080"
)

/**
 * Einfache Heuristik, um Shopify-Handles in CSS-Namen zu mappen.
 * Beispiele:
 *  - color__light_blue  -> lightblue
 *  - color__dark-grey   -> darkgray
 *  - color__navy_blue   -> navy
 *  - color__fuschia     -> fuchsia (Tippfehler)
 */
fun normalizeToCssKeyword(raw: String): String? {
    val base = raw.lowercase()
        .replace("color__", "")
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replace(Regex("\\s+"), " ")

    // eindeutige Ausnahmen
    if (base in listOf("multicolor", "multi color", "rainbow", "various", "assorted"))
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
        if (candidate in cssNamedHex) return candidate
    }

    // ohne Leerzeichen probieren
    val noSpace = base.replace(" ", "")
    if (noSpace in cssNamedHex) return noSpace

    // direkter Treffer?
    if (base in cssNamedHex) return base

    // kein Mapping gefunden
    return null
}

fun hexToColorOrNull(hex: String?): Color? =
    hex?.let { h ->
        val clean = h.removePrefix("#")
        val r = clean.substring(0, 2).toInt(16)
        val g = clean.substring(2, 4).toInt(16)
        val b = clean.substring(4, 6).toInt(16)
        Color(r, g, b)
    }

fun buildColorTriplesFromShopify(): List<Triple<Int, String, Color?>> {
    val url = "https://raw.githubusercontent.com/Shopify/product-taxonomy/refs/heads/main/data/values.yml"
    val all = fetchShopifyValuesYaml(url)

    return all.asSequence()
        .filter { it.handle?.startsWith("color__") == true && it.id != null }
        .map { v ->
            val colorKey = v.handle!!.substringAfter("color__")
            val cssKey = normalizeToCssKeyword(v.handle!!)
            val color = cssKey?.let { key -> hexToColorOrNull(cssNamedHex[key]) }
            Triple(v.id!!, colorKey, color) // Dritter Wert ist null, falls kein Mapping
        }
        .sortedBy { it.first }
        .toList()
}

fun main() {
    val list = buildColorTriplesFromShopify()
    // Beispielausgabe
    println("Erzeugt ${list.size} Farb-Tripel.")
    println(list.joinToString(prefix = "listOf(\n  ", postfix = "\n) …", separator = ",\n  ") {
        val (id, name, color) = it
        val c = color?.let { "Color(${it.red}, ${it.green}, ${it.blue})" } ?: "null"
        "Triple($id, \"$name\", $c)"
    })
}
