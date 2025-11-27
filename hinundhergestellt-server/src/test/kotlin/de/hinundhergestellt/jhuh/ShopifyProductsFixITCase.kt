package de.hinundhergestellt.jhuh

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.tools.ImageDirectoryService
import de.hinundhergestellt.jhuh.tools.ShopifyImageTools
import de.hinundhergestellt.jhuh.tools.productNameForImages
import de.hinundhergestellt.jhuh.tools.toFileNamePart
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.client.MetaobjectField
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMediaClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetaobjectClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.taxonomy.ShopifyColorTaxonomyProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.awt.Color
import java.time.OffsetDateTime
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@Disabled("Only run manually")
class ShopifyProductsFixITCase {

    @Autowired
    private lateinit var shopifyDataStore: ShopifyDataStore

    @Autowired
    private lateinit var artooDataStore: ArtooDataStore

    @Autowired
    private lateinit var shopifyImageTools: ShopifyImageTools

    @Autowired
    private lateinit var shopTexterService: ShopTexterService

    @Autowired
    private lateinit var productClient: ShopifyProductClient

    @Autowired
    private lateinit var mediaClient: ShopifyMediaClient

    @Autowired
    private lateinit var metaobjectClient: ShopifyMetaobjectClient

    @MockitoBean
    private lateinit var imageDirectoryService: ImageDirectoryService

    @Test
    fun reorganizeProducts() = runBlocking {
        shopifyDataStore.products
            .filter {
//                it.seoTitle.isNullOrEmpty()
                it.title.contains("Mini-Häuschen")
            }
            .forEach {
                reorganizeProduct(it, "nonbrand")
                normalizeImagesToUrlHandle(it)
            }
    }

    @Test
    fun normalizeImagesToUrlHandle() = runBlocking {
        shopifyDataStore.products
            .filter {
                it.title in listOf(
                    "myboshi Fellino Kunstfellgarn – 100 g Knäuel",
                    "myboshi No.2 Baumwolle-Kapok Garn – 50 g Knäuel",
                    "myboshi Samt Chenillegarn – 100 g Knäuel",
                )
            }
            .forEach { normalizeImagesToUrlHandle(it) }
    }

    private suspend fun reorganizeProduct(product: ShopifyProduct, flavor: String) {
        val reworked = shopTexterService.reworkProductTexts(product, flavor)

        val checkFolder = Path("/media/lordjaxom/akv-soft.de/sascha/Hin- und Hergestellt/Shopify")
            .resolve(product.vendor)
            .resolve(reworked.title.productNameForImages)
        if (checkFolder.exists()) {
            println("WARNING: Target folder '$checkFolder' already exists, change title!")
            if (checkFolder.exists()) {
                println("ERROR: Target folder '$checkFolder' already exists!")
                return
            }
        }

        if (shopifyDataStore.products.any { it.handle == reworked.handle && it.id != product.id }) {
            println("WARNING: Handle '${product.handle}' already exists for another product!")
            if (shopifyDataStore.products.any { it.handle == reworked.handle && it.id != product.id }) {
                println("ERROR: Handle '${product.handle}' already exists for another product!")
                return
            }
        }

        if (shopifyDataStore.products.any { it.handle == reworked.handle && it.id != product.id }) {
            println("ERROR: Handle '${reworked.handle}' already exists for another product!")
            return
        }
        if (reworked.handle != product.handle) {
            println("Handle: ${product.handle} to ${reworked.handle}")
            product.handle = reworked.handle
        }
        if (reworked.title != product.title) {
            val oldFolder = Path("/media/lordjaxom/akv-soft.de/sascha/Hin- und Hergestellt/Shopify")
                .resolve(product.vendor)
                .resolve(product.title.substringBefore(",").replace("/", " "))
            if (oldFolder.exists()) {
                val newFolder = Path("/media/lordjaxom/akv-soft.de/sascha/Hin- und Hergestellt/Shopify")
                    .resolve(product.vendor)
                    .resolve(reworked.title.productNameForImages)
                if (oldFolder != newFolder) {
                    println("Renaming image folder: $oldFolder to $newFolder")
                    oldFolder.moveTo(newFolder)
                }
            }

            val artoo =
                artooDataStore.findAllProducts().first { it.barcodes.any { barcode -> product.findVariantByBarcode(barcode) != null } }
            if (artoo.description != reworked.title) {
                println("Updating Artoo product name: ${artoo.description} to ${reworked.title}")
                artoo.description = reworked.title
                artooDataStore.update(artoo)
            }

            println("Title: ${product.title} to ${reworked.title}")
            product.title = reworked.title
        }
        if (reworked.seoTitle != product.seoTitle) {
            println("SEO Title: ${product.seoTitle} to ${reworked.seoTitle}")
            product.seoTitle = reworked.seoTitle
        }
        if (reworked.seoDescription != product.seoDescription) {
            println("SEO Description: ${product.seoDescription} to ${reworked.seoDescription}")
            product.seoDescription = reworked.seoDescription
        }
        if (reworked.descriptionHtml != product.descriptionHtml) {
            product.descriptionHtml = reworked.descriptionHtml
        }

        productClient.update(product)
    }

    private suspend fun normalizeImagesToUrlHandle(product: ShopifyProduct) {
        val changedMedia = mutableSetOf<ShopifyMedia>()
        product.media.forEach { media ->
            Regex("-produktbild-[0-9]+\\.(png|jpg)$").find(media.fileName)?.also {
                val newFileName = "${product.handle}${it.groupValues[0]}"
                if (media.fileName != newFileName) {
                    println("Renaming image file: '${media.fileName}' to '$newFileName'")
                    media.fileName = newFileName
                    changedMedia.add(media)
                }
                val newAltText = shopifyImageTools.generateAltText(product)
                if (media.altText != newAltText) {
                    println("Updating alt text '${media.altText}' to '$newAltText'")
                    media.altText = newAltText
                    changedMedia.add(media)
                }
            }
        }
        if (!product.hasOnlyDefaultVariant) {
            product.variants.forEach { variant ->
                val variantMedia = product.media.first { variant.mediaId == it.id }
                val newFileName = "${product.handle}-${variant.title.toFileNamePart()}.${variantMedia.fileName.substringAfterLast(".")}"
                if (variantMedia.fileName != newFileName) {
                    println("Renaming variant image file: '${variantMedia.fileName}' to '$newFileName'")
                    variantMedia.fileName = newFileName
                    changedMedia.add(variantMedia)
                }
                val newAltText = shopifyImageTools.generateAltText(product, variant)
                if (variantMedia.altText != newAltText) {
                    println("Updating alt text '${variantMedia.altText}' to '$newAltText'")
                    variantMedia.altText = newAltText
                    changedMedia.add(variantMedia)
                }
            }
        }

        changedMedia.chunked(10).forEach {
            println("Updating next chunk of ${it.size} media items")
            mediaClient.update(it)
        }
    }

    @Test
    fun fixUrlHandle() = runBlocking {
        fixUrlHandle("superior-9500-jewel-metallic-a4", "superior-9500-jewel-metallic-vinylfolie-a4")
    }

    private suspend fun fixUrlHandle(oldHandle: String, newHandle: String) {
        val product = shopifyDataStore.products.first { it.handle == oldHandle }
        product.handle = newHandle
        productClient.update(product)
        normalizeImagesToUrlHandle(product)
    }

    @Test
    fun unorganizedShopifyImages() = runBlocking {
        val product = shopifyDataStore.products.first { it.title == "POLI-FLEX® PEARL GLITTER Flexfolie – A4 Bogen (20×30 cm)" }
        assertEquals(0, shopifyImageTools.unorganizedProductImages(product).size)
    }

    @Test
    fun locallyMissingShopifyImages() = runBlocking {
        val product = shopifyDataStore.products.first { it.title == "POLI-FLEX® PEARL GLITTER Flexfolie – A4 Bogen (20×30 cm)" }
        assertEquals(0, shopifyImageTools.locallyMissingProductImages(product).size)
    }

    @Test
    fun fixMissingSpecialCharsInMeta() = runBlocking {
        shopifyDataStore.products.forEach { product ->
            if (product.title.contains("®") && product.seoTitle?.contains("®") == false) {
                val brandName = product.title.substringBefore("®")
                product.seoTitle = product.seoTitle!!.replace(brandName, "${brandName}®")
                productClient.update(product)
            }
        }
    }

    @Test
    fun fixAltTextsWithMissingSpace() = runBlocking {
        val media = shopifyDataStore.products
            .flatMap { it.media }
            .filter { it.altText.contains(Regex("Variante:[^ ]")) }
            .onEach { it.altText = it.altText.replace("Variante:", "Variante: ") }
        media.chunked(10).forEach {
            println("Updating next chunk of ${it.size} media items")
            mediaClient.update(it)
        }
    }

    @Test
    fun findAllBarcodePrefixes() {
        artooDataStore.findAllProducts()
            .flatMap { it.variations }
            .filter { !it.barcode.isNullOrEmpty() }
            .sortedBy { it.barcode }
            .forEach { println("${it.barcode} ${it.parent.name} ${it.name}") }
    }

    @Test
    fun findProductsWithoutSeo() = runBlocking {
        shopifyDataStore.products
            .filter { it.seoTitle.isNullOrEmpty() || it.seoDescription.isNullOrEmpty() }
            .forEach { println(it.title) }
    }

    @Test
    fun fixLocalImageFolders() = runBlocking {
        shopifyDataStore.products.forEach { product ->
            val oldFolder = Path("/media/lordjaxom/akv-soft.de/sascha/Hin- und Hergestellt/Shopify")
                .resolve(product.vendor)
                .resolve(product.title.substringBefore(",").replace("/", " "))
            if (oldFolder.exists()) {
                val newFolder = Path("/media/lordjaxom/akv-soft.de/sascha/Hin- und Hergestellt/Shopify")
                    .resolve(product.vendor)
                    .resolve(product.title.productNameForImages)
                if (oldFolder != newFolder) {
                    println("Renaming image folder: $oldFolder to $newFolder")
                    oldFolder.moveTo(newFolder)
                }
            }
        }
    }

    @Test
    fun fixBaseColors() = runBlocking {
        var dirty = false
        val metaobjects = metaobjectClient.fetchDefinitionByType("shopify--color-pattern")!!.metaobjects
        metaobjects.forEach { metaobject ->
            if (metaobject.updatedAt!!.isAfter(OffsetDateTime.of(2025, 9, 1, 0, 0, 0, 0, OffsetDateTime.now().offset))) {
                return@forEach
            }

            val label = metaobject.fields.first { it.key == "label" }.value!!
            val oldColorField = metaobject.fields.first { it.key == "color" }
            val oldColorValue = colorFromHex(oldColorField.value!!)
            val oldTaxonomyField = metaobject.fields.first { it.key == "color_taxonomy_reference" }
            val oldTaxonomy = ShopifyColorTaxonomyProvider.colors[(oldTaxonomyField.jsonValue as ArrayNode)[0].textValue()]!!

            if (label.contains(oldTaxonomy.name, ignoreCase = true)) return@forEach
            if (label.contains("grey"))
            if (label.contains("metallic", ignoreCase = true)) return@forEach

            val newTaxonomies = classifyBaseColorLoose(oldColorValue, label)
                .map { colorName -> ShopifyColorTaxonomyProvider.colors.values.first { it.name == colorName } }

            if (newTaxonomies.size > 1 || newTaxonomies[0].id != oldTaxonomy.id) {
                println("$label: ${oldTaxonomy.name} -> ${newTaxonomies.map { it.name }}")

                metaobject.fields -= oldTaxonomyField

                val newTaxonomyIds = newTaxonomies.map { it.id }
                metaobject.fields += MetaobjectField("color_taxonomy_reference", jacksonObjectMapper().writeValueAsString(newTaxonomyIds))
                dirty = true
            }
        }

        if (dirty) {
            println("Updating")
        }
    }
}

fun colorFromHex(hex: String): Color {
    val clean = hex.removePrefix("#").trim()
    val value = clean.toLong(16)
    return when (clean.length) {
        6 -> Color(((value shr 16) and 0xFF).toInt(), ((value shr 8) and 0xFF).toInt(), (value and 0xFF).toInt())
        8 -> Color(
            ((value shr 16) and 0xFF).toInt(),
            ((value shr 8) and 0xFF).toInt(),
            (value and 0xFF).toInt(),
            ((value shr 24) and 0xFF).toInt()
        )

        else -> throw IllegalArgumentException("Invalid hex color: $hex")
    }
}

// ---------- HSL-Utils ----------
data class HSL(val h: Double, val s: Double, val l: Double) // h: [0,360), s/l: [0,1]

fun Color.toHsl(): HSL {
    val r = red / 255.0; val g = green / 255.0; val b = blue / 255.0
    val maxc = max(r, max(g, b))
    val minc = min(r, min(g, b))
    val l = (maxc + minc) / 2.0
    val d = maxc - minc
    if (d == 0.0) return HSL(0.0, 0.0, l)
    val s = if (l > 0.5) d / (2.0 - maxc - minc) else d / (maxc + minc)
    val h = when (maxc) {
        r -> (g - b) / d + (if (g < b) 6 else 0)
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    } * 60.0
    return HSL(h % 360.0, s, l)
}
private fun hueDelta(a: Double, b: Double): Double {
    val d = abs(a - b) % 360.0
    return min(d, 360.0 - d)
}

// ---------- Klassifikation ----------
/**
 * Gibt 1..2 Basisfarben zurück (Shopify-BaseColor-Namen), z. B. ["Green"], oder ["Green","Blue"] für Mint/Türkis.
 * Metallic (Gold/Silver/Bronze) wird NUR gemappt, wenn der Name entsprechende Keywords enthält.
 */
fun classifyBaseColor(rgb: Color, displayName: String? = null): List<String> {
    val (h, s, l) = rgb.toHsl()
    val name = (displayName ?: "").lowercase()

    // Sonderfälle Metall NUR mit Namens-Hinweis
    val isMetallic = listOf("metal", "metall", "metallic", "foil", "glitter").any { name.contains(it) }
    if (isMetallic) {
        // warm -> Gold/Bronze, kühl/grau -> Silver
        return when {
            s < 0.18 && l in 0.35..0.80 -> listOf("Silver")
            h in 20.0..60.0            -> listOf("Gold")
            h in 15.0..40.0 && l < 0.55 -> listOf("Bronze")
            else                        -> listOf("Silver") // fallback metallisch
        }
    }

    // Transparent?
    if (name.contains("clear") || name.contains("transparent") || name.contains("transparent".lowercase())) {
        return listOf("Clear")
    }

    // 1) Graustufen & sehr entsättigt → Black/Gray/White/Beige
    if (s <= 0.10) {
        return when {
            l <= 0.18 -> listOf("Black")
            l >= 0.87 -> listOf("White")
            else      -> listOf("Gray")
        }
    }
    // sehr hell & leicht entsättigt → Beige
    if (s < 0.25 && l in 0.70..0.95 && (h in 20.0..70.0)) {
        return listOf("Beige")
    }

    // 2) Navy vs Blue
    if (h in 200.0..255.0) {
        if (l < 0.25) return listOf("Navy")
        // Pastell-Eisblau → Blue (optional zweite Farbe Gray wenn sehr entsättigt)
        return if (s < 0.22 && l > 0.70) listOf("Blue") else listOf("Blue")
    }

    // 3) Mint/Türkis → Green & Blue (zwei Tags) bei cyanlastigen Bereichen
    if (h in 170.0..200.0) {
        return if (l > 0.45) listOf("Green", "Blue") else listOf("Blue", "Green")
    }

    // 4) Grün
    if (h in 70.0..170.0) {
        // sehr dunkles Grün?
        return listOf("Green")
    }

    // 5) Gelb
    if (h in 45.0..70.0) {
        return if (l < 0.35) listOf("Brown") else listOf("Yellow")
    }

    // 6) Orange / Braun (orange/gelb Hues mit L-Abhängig)
    if (h in 15.0..45.0) {
        return if (l < 0.48) listOf("Brown") else listOf("Orange")
    }

    // 7) Rot
    if (h >= 345.0 || h < 15.0) {
        // sehr hell/satt Richtung Pink?
        return if (l > 0.65 && s > 0.35) listOf("Pink") else listOf("Red")
    }

    // 8) Magenta/Pink (290..345)
    if (h in 290.0..345.0) {
        return if (l > 0.45) listOf("Pink") else listOf("Red")
    }

    // 9) Purple/Violett (255..290)
    if (h in 255.0..290.0) return listOf("Purple")

    // 10) Fallbacks
    // wenn fast grau aber warm → Beige, sonst Gray
    return if (s < 0.2 && l > 0.75 && (h in 20.0..70.0)) listOf("Beige") else listOf("Gray")
}

/** Optional: wenn Farbe knapp zwischen zwei Sektoren liegt, beide zurückgeben (Boundary ±8°). */
fun classifyBaseColorLoose(rgb: Color, displayName: String? = null): List<String> {
    val (h, s, l) = rgb.toHsl()
    fun inRange(a: Double, b: Double, deg: Double = 8.0) = hueDelta(h, (a + b) / 2) <= (b - a) / 2 + deg
    val primary = classifyBaseColor(rgb, displayName).toMutableList()
    // Mint-Grenzen
    if (inRange(170.0, 200.0)) return listOf("Green","Blue")
    // Blue/Navy Grenze
    if (h in 200.0..255.0 && l in 0.22..0.28) return listOf("Blue","Navy")
    // Orange/Brown Grenze
    if (inRange(15.0,45.0) && l in 0.42..0.52) primary += "Brown"
    // Yellow/Orange Grenze
    if (inRange(45.0,70.0) && l in 0.40..0.50) primary += "Orange"
    // Red/Pink Grenze
    if ((h >= 345.0 || h < 15.0) && l > 0.60 && s > 0.35) primary += "Pink"
    return primary.distinct()
}