package de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model

import com.fasterxml.jackson.annotation.JsonInclude

@Suppress("unused")
abstract class ProductsPostRequestProductBaseMixin {

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    abstract val productId: Int?
}