package de.hinundhergestellt.jhuh.vendors.ready2order.client

import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.ApiClient
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.api.ProductGroupApi
import org.springframework.stereotype.Component

@Component
class ArtooProductGroupClient(
    apiClient: ApiClient
) {
    private val api: ProductGroupApi = ProductGroupApi(apiClient)

    fun findAll() = pageAll {
        api.productgroupsGet(it, null)
            .map { group -> ArtooProductGroup(group) }
    }

    fun create(group: UnsavedArtooProductGroup) =
        ArtooProductGroup(api.productgroupsPost(group.toProductgroupsPostRequest()))

    fun update(group: ArtooProductGroup) {
        api.productgroupsIdPut(group.id, group.toProductgroupsPostRequest())
    }

    fun delete(group: ArtooProductGroup) {
        api.productgroupsIdDelete(group.id)
    }
}
