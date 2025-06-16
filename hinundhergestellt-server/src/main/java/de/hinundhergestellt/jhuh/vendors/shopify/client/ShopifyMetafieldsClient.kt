package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode
import com.shopify.admin.client.MetafieldsDeleteGraphQLQuery
import com.shopify.admin.client.MetafieldsDeleteProjectionRoot
import com.shopify.admin.types.MetafieldsDeletePayload
import org.springframework.stereotype.Component

@Component
class ShopifyMetafieldsClient(
    private val apiClient: GraphQLClient
) {
    fun delete(product: ShopifyProduct, metafields: List<ShopifyMetafield>) {
        val query = MetafieldsDeleteGraphQLQuery.newRequest()
            .metafields(metafields.map { it.toMetafieldIdentifierInput(product.id) })
            .build()

        // @formatter:off
        val root = MetafieldsDeleteProjectionRoot<BaseSubProjectionNode<*, *>, BaseSubProjectionNode<*, *>>()
            .userErrors()
                .message()
                .field()
        // @formatter:on

        apiClient.executeMutation(query, root, MetafieldsDeletePayload::getUserErrors)
    }
}