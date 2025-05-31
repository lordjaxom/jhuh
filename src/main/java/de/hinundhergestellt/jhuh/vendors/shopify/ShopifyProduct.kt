package de.hinundhergestellt.jhuh.vendors.shopify

import com.shopify.admin.types.OptionCreateInput
import com.shopify.admin.types.OptionValueCreateInput
import com.shopify.admin.types.Product
import com.shopify.admin.types.ProductCreateInput
import com.shopify.admin.types.ProductOption

class ShopifyProduct internal constructor(
    private val product: Product
) {
    var id: String? by product::id
    var title: String by product::title
    var vendor: String by product::vendor
    var productType: String by product::productType
    var tags: List<String> by product::tags
    val options: List<ProductOption> by product::options

    var variants = product.variants?.edges?.asSequence()
        ?.map { ShopifyProductVariant(it.node) }
        ?.toMutableList()
        ?: mutableListOf()

    constructor(
        title: String,
        vendor: String,
        productType: String,
        tags: List<String>,
        options: List<ProductOption>
    ) : this(Product().also {
        it.title = title
        it.vendor = vendor
        it.productType = productType
        it.tags = tags
        it.options = options
    })

    fun findVariantByBarcode(barcode: String) =
        variants.firstOrNull { it.barcode == barcode }

    fun toProductCreateInput() = ProductCreateInput().apply {
        title = product.title
        vendor = product.vendor
        productType = product.productType
        tags = product.tags
        productOptions = product.options.map { option ->
            OptionCreateInput().apply {
                name = option.name
                values = option.values.map { value ->
                    OptionValueCreateInput().apply {
                        name = value
                    }
                }
            }
        }
    }
}