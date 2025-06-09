package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest
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

        val request = GraphQLQueryRequest(query, root)
        val response = apiClient.executeQuery(request.serialize())
        require(!response.hasErrors()) { "Product options delete failed: " + response.errors }
        val payload = response.extractValueAsObject("productOptionsDelete", ProductOptionsDeletePayload::class.java)
        require(payload.userErrors.isEmpty()) { "Product options delete failed: " + payload.userErrors }
    }
}