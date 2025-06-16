package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode
import com.shopify.admin.client.ProductOptionsDeleteGraphQLQuery
import com.shopify.admin.client.ProductOptionsDeleteProjectionRoot
import com.shopify.admin.types.ProductOptionsDeletePayload
import org.springframework.stereotype.Component

@Component
class ShopifyProductOptionClient(
    private val apiClient: GraphQLClient
) {
    fun delete(product: ShopifyProduct, options: List<ShopifyProductOption>) {
        val query = ProductOptionsDeleteGraphQLQuery.newRequest()
            .productId(product.id)
            .options(options.map { it.id })
            .build()

        // @formatter:off
        val root = ProductOptionsDeleteProjectionRoot<BaseSubProjectionNode<*, *>, BaseSubProjectionNode<*, *>>()
            .userErrors()
                .message()
                .field()
        // @formatter:on

        apiClient.executeMutation(query, root, ProductOptionsDeletePayload::getUserErrors)
    }
}