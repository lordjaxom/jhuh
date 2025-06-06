package de.hinundhergestellt.jhuh.vendors.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.ApiClient
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.api.ProductApi
import org.springframework.stereotype.Component

@Component
class ArtooProductClient(
    apiClient: ApiClient
) {
    private val api = ProductApi(apiClient)

    fun findAll() = pageAll {
        api.productsGet(it, null, null, null, null, null, null, true, null, null)
            .map { product -> ArtooProduct(product) }
    }

    fun findById(id: Int): ArtooProduct {
        return ArtooProduct(api.productsIdGet(id, true, null, null))
    }

    fun save(product: ArtooProduct) {
        product.save(api)
    }

    fun delete(product: ArtooProduct) {
        api.productsIdDelete(product.id)
    }
}
