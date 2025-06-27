package de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model

import com.fasterxml.jackson.annotation.JsonIgnore

@Suppress("unused")
abstract class ProductsIdPutRequestMixin {

    @get:JsonIgnore
    abstract val productVatId: Int?

    @get:JsonIgnore
    abstract val productgroupId: Int?
}
