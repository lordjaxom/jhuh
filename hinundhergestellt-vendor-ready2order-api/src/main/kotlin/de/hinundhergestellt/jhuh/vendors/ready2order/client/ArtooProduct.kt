package de.hinundhergestellt.jhuh.vendors.ready2order.client

import de.hinundhergestellt.jhuh.core.DirtyTracker
import de.hinundhergestellt.jhuh.core.HasDirtyTracker
import de.hinundhergestellt.jhuh.core.fixedScale
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsGet200ResponseInner
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsIdPutRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestProductBase
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestProductgroup
import java.math.BigDecimal

open class UnsavedArtooProduct(
    name: String,
    itemNumber: String?,
    barcode: String?,
    description: String,
    price: BigDecimal,
    priceIncludesVat: Boolean,
    vat: BigDecimal,
    stockEnabled: Boolean,
    variationsEnabled: Boolean = false,
    stockValue: BigDecimal,
    stockUnit: String? = if (stockEnabled) "piece" else null,
    stockReorderLevel: BigDecimal = BigDecimal.ZERO,
    stockSafetyStock: BigDecimal = BigDecimal.ZERO,
    sortIndex: Int = 0,
    active: Boolean,
    discountable: Boolean = true,
    type: ArtooProductType = ArtooProductType.INHERITED,
    baseId: Int? = null,
    productGroupId: Int,
    alternativeNameInPos: String
): HasDirtyTracker {

    override val dirtyTracker = DirtyTracker()

    var name by dirtyTracker.track(name)
    var itemNumber by dirtyTracker.track(itemNumber)
    var barcode by dirtyTracker.track(barcode)
    var description by dirtyTracker.track(description)
    var priceIncludesVat by dirtyTracker.track(priceIncludesVat)
    var price by dirtyTracker.track(fixedScale(price, 2))
    var vat by dirtyTracker.track(fixedScale(vat, 2))
    var stockEnabled by dirtyTracker.track(stockEnabled)
    var variationsEnabled by dirtyTracker.track(variationsEnabled)
    var stockValue by dirtyTracker.track(fixedScale(stockValue, 0))
    var stockUnit by dirtyTracker.track(stockUnit)
    var stockReorderLevel by dirtyTracker.track(fixedScale(stockReorderLevel, 0))
    var stockSafetyStock by dirtyTracker.track(fixedScale(stockSafetyStock, 0))
    var sortIndex by dirtyTracker.track(sortIndex)
    var active by dirtyTracker.track(active)
    var discountable by dirtyTracker.track(discountable)
    var type by dirtyTracker.track(type)
    var baseId by dirtyTracker.track(baseId)
    var productGroupId by dirtyTracker.track(productGroupId)
    var alternativeNameInPos by dirtyTracker.track(alternativeNameInPos)

    override fun toString() =
        "UnsavedArtooProduct(name='$name', description='$description', itemNumber='$itemNumber', barcode='$barcode')"

    internal fun toProductsPostRequest() =
        ProductsPostRequest(
            productName = name,
            productItemnumber = itemNumber,
            productBarcode = barcode,
            productDescription = description,
            productPrice = price.toPlainString(),
            productPriceIncludesVat = priceIncludesVat,
            productActive = active,
            productDiscountable = discountable,
            productVat = vat.toPlainString(),
            productStockEnabled = stockEnabled,
            productVariationsEnabled = variationsEnabled,
            productStockValue = stockValue.toPlainString(),
            productStockReorderLevel = stockReorderLevel.toPlainString(),
            productStockSafetyStock = stockSafetyStock.toPlainString(),
            productStockUnit = stockUnit,
            productSortIndex = sortIndex,
            productType = type.type,
            productBase = toProductsPostRequestProductBase(),
            productgroup = toProductsPostRequestProductgroup(),
            productAlternativeNameInPos = alternativeNameInPos,
        )

    protected fun toProductsPostRequestProductBase() =
        ProductsPostRequestProductBase(baseId)

    protected fun toProductsPostRequestProductgroup() =
        ProductsPostRequestProductgroup(productGroupId)
}

class ArtooProduct : UnsavedArtooProduct {

    val id: Int

    internal constructor(product: ProductsGet200ResponseInner) : super(
        product.productName!!,
        product.productItemnumber,
        product.productBarcode,
        product.productDescription!!,
        BigDecimal(product.productPrice),
        product.productPriceIncludesVat!!,
        BigDecimal(product.productVat),
        product.productStockEnabled!!,
        product.productVariationsEnabled!!,
        BigDecimal(product.productStockValue),
        product.productStockUnit,
        product.productStockReorderLevel?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
        product.productStockSafetyStock?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
        product.productSortIndex!!,
        product.productActive!!,
        product.productDiscountable!!,
        ArtooProductType.valueOf(product.productTypeId),
        product.productBaseId,
        product.productgroupId ?: product.productgroup!!.productgroupId!!,
        product.productAlternativeNameInPos ?: ""
    ) {
        id = product.productId!!
    }

    override fun toString() =
        "ArtooProduct(id=$id, name='$name', description='$description', itemNumber='$itemNumber', barcode='$barcode')"

    internal fun toProductsIdPutRequest() =
        ProductsIdPutRequest(
            productName = name,
            productItemnumber = itemNumber,
            productBarcode = barcode,
            productDescription = description,
            productPrice = price.toPlainString(),
            productPriceIncludesVat = priceIncludesVat,
            productActive = active,
            productDiscountable = discountable,
            productVat = vat.toPlainString(),
            productStockEnabled = stockEnabled,
            productVariationsEnabled = variationsEnabled,
            productStockValue = stockValue.toPlainString(),
            productStockReorderLevel = stockReorderLevel.toPlainString(),
            productStockSafetyStock = stockSafetyStock.toPlainString(),
            productStockUnit = stockUnit,
            productSortIndex = sortIndex,
            productType = type.type,
            productBase = toProductsPostRequestProductBase(),
            productgroup = toProductsPostRequestProductgroup(),
            productAlternativeNameInPos = alternativeNameInPos,
        )
}

enum class ArtooProductType(
    val id: Int?,
    val type: String?
) {
    INHERITED(null, null),
    VARIATION(5, "variation"),
    STANDARD(7, "standard");

    companion object {
        fun valueOf(id: Int?) = entries.find { it.id == id }!!
    }
}