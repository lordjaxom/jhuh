package de.hinundhergestellt.jhuh.vendors.shopify

import com.shopify.admin.types.Product
import com.shopify.admin.types.ProductVariant
import com.shopify.admin.types.ProductVariantEdge
import org.springframework.util.Assert

class ShopifyProduct internal constructor(private val product: Product) {

    val handle: String by product::handle
    val id: String by product::id

    var title: String by product::title
    var productType: String by product::productType
    var tags: List<String> by product::tags
    var vendor: String by product::vendor
    val hasOnlyDefaultVariant: Boolean by product::hasOnlyDefaultVariant

    val variants: Sequence<ProductVariant>
        get() {
            require(!product.variants.pageInfo.hasNextPage) { "Product has more variants than were loaded" }
            return product.variants.edges.asSequence().map { it.node }
        }
}
