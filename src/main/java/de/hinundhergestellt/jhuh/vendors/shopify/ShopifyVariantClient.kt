package de.hinundhergestellt.jhuh.vendors.shopify

import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest
import com.shopify.admin.client.ProductVariantsBulkCreateGraphQLQuery
import com.shopify.admin.client.ProductVariantsBulkCreateProjectionRoot
import com.shopify.admin.client.ProductVariantsBulkDeleteGraphQLQuery
import com.shopify.admin.client.ProductVariantsBulkDeleteProjectionRoot
import com.shopify.admin.types.ProductVariantsBulkCreatePayload
import com.shopify.admin.types.ProductVariantsBulkDeletePayload
import org.springframework.stereotype.Component

@Component
class ShopifyVariantClient(
    private val apiClient: GraphQLClient
) {
    fun create(product: ShopifyProduct, variants: List<ShopifyVariant>) {
        val inputs = variants
            .onEach { require(it.id == null) { "Variant id must be null" } }
            .map { it.toProductVariantsBulkInput() }

        val query = ProductVariantsBulkCreateGraphQLQuery.newRequest()
            .productId(product.id)
            .variants(inputs)
            .build()

        // @formatter:off
        val root = ProductVariantsBulkCreateProjectionRoot<BaseSubProjectionNode<*, *>, BaseSubProjectionNode<*, *>>()
            .userErrors()
                .message()
                .field()
        // @formatter:on

        val request = GraphQLQueryRequest(query, root)
        val response = apiClient.executeQuery(request.serialize())
        require(!response.hasErrors()) { "Product variants bulk create failed: " + response.errors }
        val payload = response.extractValueAsObject("productVariantsBulkCreate", ProductVariantsBulkCreatePayload::class.java)
        require((payload.userErrors.isEmpty())) { "Product variants bulk create failed: " + payload.userErrors }
    }

    fun delete(product: ShopifyProduct, variants: List<ShopifyVariant>) {
        val query = ProductVariantsBulkDeleteGraphQLQuery.newRequest()
            .productId(product.id)
            .variantsIds(variants.map { it.id })
            .build()

        // @formatter:off
        val root = ProductVariantsBulkDeleteProjectionRoot<BaseSubProjectionNode<*, *>, BaseSubProjectionNode<*, *>>()
            .userErrors()
                .message()
                .field()
        // @formatter:on

        val request = GraphQLQueryRequest(query, root)
        val response = apiClient.executeQuery(request.serialize())
        require(!response.hasErrors()) { "Product variants delete failed: " + response.errors }
        val payload = response.extractValueAsObject("productVariantsBulkDelete", ProductVariantsBulkDeletePayload::class.java)
        require(payload.userErrors.isEmpty()) { "Product variants delete failed: " + payload.userErrors }
    }
}