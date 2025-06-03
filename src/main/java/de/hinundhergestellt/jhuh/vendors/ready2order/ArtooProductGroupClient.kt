package de.hinundhergestellt.jhuh.vendors.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.api.ProductGroupApi
import org.springframework.stereotype.Component
import java.util.function.Function
import java.util.stream.Collectors

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
