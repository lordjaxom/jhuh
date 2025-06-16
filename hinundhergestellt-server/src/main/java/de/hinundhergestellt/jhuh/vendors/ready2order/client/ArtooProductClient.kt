package de.hinundhergestellt.jhuh.vendors.ready2order.client

import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.ApiClient
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.api.ProductApi
import org.springframework.stereotype.Component

@Component
class ArtooProductClient(
    apiClient: ApiClient
) {
    private val api = ProductApi(apiClient)

    fun findAll(name: String? = null) = pageAll {
        api.productsGet(it, null, null, null, name, null, null, true, null, null)
            .map { product -> ArtooProduct(product) }
    }

    fun findById(id: Int) =
        ArtooProduct(api.productsIdGet(id, true, null, null))

    fun create(product: UnsavedArtooProduct) =
        ArtooProduct(api.productsPost(product.toProductsPostRequest()))

    fun update(product: ArtooProduct) {
        api.productsIdPut(product.id, product.toProductsIdPutRequest())
    }

    fun delete(product: ArtooProduct) {
        api.productsIdDelete(product.id)
    }
}
