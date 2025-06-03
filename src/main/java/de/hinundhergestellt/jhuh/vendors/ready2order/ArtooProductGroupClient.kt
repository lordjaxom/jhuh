package de.hinundhergestellt.jhuh.vendors.ready2order

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

    fun save(productGroup: ArtooProductGroup) {
        productGroup.save(api)
    }
}
