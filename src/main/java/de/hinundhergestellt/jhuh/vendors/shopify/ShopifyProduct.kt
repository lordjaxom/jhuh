package de.hinundhergestellt.jhuh.vendors.shopify

import com.shopify.admin.types.Product
import com.shopify.admin.types.ProductOption

class ShopifyProduct internal constructor(
    private val product: Product
) {
    val handle: String by product::handle
    val id: String by product::id

    var title: String by product::title
    var productType: String by product::productType
    var tags: List<String> by product::tags
    var vendor: String by product::vendor
    val hasOnlyDefaultVariant: Boolean by product::hasOnlyDefaultVariant
    val options: List<ProductOption> by product::options

    val variants
        get() = product.variants.edges.asSequence().map { ShopifyVariant(it.node) }

    fun findVariantByBarcode(barcode: String) =
        variants.firstOrNull { it.barcode == barcode }

    internal fun addVariant(variant: ShopifyVariant) {
        product.variants.edges.add(variant.toEdge())
    }

    internal fun removeVariants(variants: List<ShopifyVariant>) {
        product.variants.edges.removeIf { edge -> variants.any { it.id == edge.node!!.id } }
    }
}