package de.hinundhergestellt.jhuh.vendors.ready2order.client

import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.api.ProductApi
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class ArtooProductClient(
    ready2orderWebClient: WebClient
) {
    private val api = ProductApi(ready2orderWebClient)

    fun findAll(name: String? = null) = pageAll {
        api.productsGet(it, null, null, null, name, null, null, true, null, null)
            .awaitSingle()
            .map { product -> ArtooProduct(product) }
    }

    suspend fun findById(id: Int) =
        ArtooProduct(api.productsIdGet(id, true, null, null).awaitSingle())

    suspend fun create(product: UnsavedArtooProduct) =
        ArtooProduct(api.productsPost(product.toProductsPostRequest()).awaitSingle())

    suspend fun update(product: ArtooProduct) {
        api.productsIdPut(product.id, product.toProductsIdPutRequest()).awaitSingle()
    }

    suspend fun delete(product: ArtooProduct) {
        api.productsIdDelete(product.id).awaitSingle()
    }
}
