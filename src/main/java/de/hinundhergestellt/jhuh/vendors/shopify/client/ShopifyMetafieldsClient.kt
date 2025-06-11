package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest
import com.shopify.admin.client.MetafieldsDeleteGraphQLQuery
import com.shopify.admin.client.MetafieldsDeleteProjectionRoot
import com.shopify.admin.types.MetafieldsDeletePayload
import com.shopify.admin.types.UserError
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

inline fun <reified PAYLOAD: Any> GraphQLClient.executeMutation(
    query: GraphQLQuery,
    root: BaseSubProjectionNode<*, *>,
    userErrors: (PAYLOAD) -> List<UserError>
) {
    val queryName = query::class.simpleName!!.removeSuffix("GraphQLQuery")
    val request = GraphQLQueryRequest(query, root)
    val response = executeQuery(request.serialize())
    require(!response.hasErrors()) { "$queryName failed: ${response.errors}" }
    val payload = response.extractValueAsObject(queryName.replaceFirstChar { it.lowercase() }, PAYLOAD::class.java)
    userErrors(payload).also { require(it.isEmpty()) { "$queryName failed: $it" } }
}