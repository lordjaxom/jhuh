package de.hinundhergestellt.jhuh.vendors.ready2order.client

import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.api.ProductGroupApi
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class ArtooProductGroupClient(
    @Qualifier("ready2orderWebClient") ready2orderWebClient: WebClient
) {
    private val api: ProductGroupApi = ProductGroupApi(ready2orderWebClient)

    suspend fun findAll() = pageAll {
        api.productgroupsGet(it, null)
            .awaitSingle()
            .map { group -> ArtooProductGroup(group) }
    }

    suspend fun create(group: UnsavedArtooProductGroup) =
        ArtooProductGroup(api.productgroupsPost(group.toProductgroupsPostRequest()).awaitSingle())

    suspend fun update(group: ArtooProductGroup) {
        api.productgroupsIdPut(group.id, group.toProductgroupsPostRequest()).awaitSingle()
    }

    suspend fun delete(group: ArtooProductGroup) {
        api.productgroupsIdDelete(group.id).awaitSingle()
    }
}
