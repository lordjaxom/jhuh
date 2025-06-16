package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode
import com.shopify.admin.client.ProductVariantsBulkCreateGraphQLQuery
import com.shopify.admin.client.ProductVariantsBulkCreateProjectionRoot
import com.shopify.admin.client.ProductVariantsBulkDeleteGraphQLQuery
import com.shopify.admin.client.ProductVariantsBulkDeleteProjectionRoot
import com.shopify.admin.client.ProductVariantsBulkUpdateGraphQLQuery
import com.shopify.admin.types.ProductVariantsBulkCreatePayload
import com.shopify.admin.types.ProductVariantsBulkCreateStrategy
import com.shopify.admin.types.ProductVariantsBulkDeletePayload
import com.shopify.admin.types.ProductVariantsBulkUpdatePayload
import org.springframework.stereotype.Component

@Component
class ShopifyProductVariantClient(
    private val apiClient: GraphQLClient
) {
    fun create(product: ShopifyProduct, variants: List<UnsavedShopifyProductVariant>): List<ShopifyProductVariant> {
        val strategy =
            if (product.variants.isEmpty()) ProductVariantsBulkCreateStrategy.REMOVE_STANDALONE_VARIANT
            else ProductVariantsBulkCreateStrategy.DEFAULT
        val query = ProductVariantsBulkCreateGraphQLQuery.newRequest()
            .strategy(strategy)
            .productId(product.id)
            .variants(variants.map { it.toProductVariantsBulkInput() })
            .build()

        // @formatter:off
        val root = ProductVariantsBulkCreateProjectionRoot<BaseSubProjectionNode<*, *>, BaseSubProjectionNode<*, *>>()
            .productVariants()
                .id()
                .title()
                .parent()
            .userErrors()
                .message()
                .field()
        // @formatter:on

        val payload = apiClient.executeMutation(query, root, ProductVariantsBulkCreatePayload::getUserErrors)
        return variants
            .zip(payload.productVariants)
            .map { (variant, created) -> ShopifyProductVariant(variant, created.id, created.title) }
    }

    fun update(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
        val query = ProductVariantsBulkUpdateGraphQLQuery.newRequest()
            .productId(product.id)
            .variants(variants.map { it.toProductVariantsBulkInput() })
            .build()

        // @formatter:off
        val root = ProductVariantsBulkCreateProjectionRoot<BaseSubProjectionNode<*, *>, BaseSubProjectionNode<*, *>>()
            .userErrors()
                .message()
                .field()
        // @formatter:on

        apiClient.executeMutation(query, root, ProductVariantsBulkUpdatePayload::getUserErrors)
    }

    fun delete(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
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

        apiClient.executeMutation(query, root, ProductVariantsBulkDeletePayload::getUserErrors)
    }
}