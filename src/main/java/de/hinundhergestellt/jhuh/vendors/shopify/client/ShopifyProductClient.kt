package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest
import com.shopify.admin.client.ProductCreateGraphQLQuery
import com.shopify.admin.client.ProductCreateProjectionRoot
import com.shopify.admin.client.ProductDeleteGraphQLQuery
import com.shopify.admin.client.ProductDeleteProjectionRoot
import com.shopify.admin.client.ProductUpdateGraphQLQuery
import com.shopify.admin.client.ProductUpdateProjectionRoot
import com.shopify.admin.client.ProductsGraphQLQuery
import com.shopify.admin.client.ProductsProjectionRoot
import com.shopify.admin.types.PageInfo
import com.shopify.admin.types.ProductConnection
import com.shopify.admin.types.ProductCreatePayload
import com.shopify.admin.types.ProductDeletePayload
import com.shopify.admin.types.ProductUpdatePayload
import org.springframework.stereotype.Component

@Component
class ShopifyProductClient(
    private val apiClient: GraphQLClient
) {
    fun findAll() = pageAll {
        findAll(it)
    }

    fun create(product: UnsavedShopifyProduct): ShopifyProduct {
        val query = ProductCreateGraphQLQuery.newRequest()
            .product(product.toProductCreateInput())
            .build()

        // @formatter:off
        val root = ProductCreateProjectionRoot<BaseSubProjectionNode<*, *>, BaseSubProjectionNode<*, *>>()
            .product()
                .id()
                .options()
                    .id()
                    .parent()
                .parent()
            .userErrors()
                .message()
                .field()
        // @formatter:on

        val payload = apiClient.executeMutation(query, root, ProductCreatePayload::getUserErrors)
        val options = product.options.asSequence()
            .zip(payload.product.options.asSequence())
            .map { (option, created) -> ShopifyProductOption(option, created.id) }
            .toMutableList()
        return ShopifyProduct(product, payload.product.id, options)
    }

    fun update(product: ShopifyProduct) {
        val query = ProductUpdateGraphQLQuery.newRequest()
            .product(product.toProductUpdateInput())
            .build()

        // @formatter:off
        val root = ProductUpdateProjectionRoot<BaseSubProjectionNode<*, *>, BaseSubProjectionNode<*, *>>()
            .userErrors()
                .message()
                .field()
        // @formatter:on

        apiClient.executeMutation(query, root, ProductUpdatePayload::getUserErrors)
    }

    fun delete(product: ShopifyProduct) {
        val query = ProductDeleteGraphQLQuery.newRequest()
            .input(product.toProductDeleteInput())
            .build()

        // @formatter:off
        val root = ProductDeleteProjectionRoot<BaseSubProjectionNode<*, *>, BaseSubProjectionNode<*, *>>()
            .userErrors()
                .message()
                .field()
        // @formatter:on

        apiClient.executeMutation(query, root, ProductDeletePayload::getUserErrors)
    }

    private fun findAll(after: String?): Pair<Sequence<ShopifyProduct>, PageInfo> {
        val query = ProductsGraphQLQuery.newRequest()
            .first(250)
            .after(after)
            .build()

        // @formatter:off
        val root = ProductsProjectionRoot<BaseSubProjectionNode<*, *>, BaseSubProjectionNode<*, *>>()
            .edges()
                .node()
                    .handle()
                    .id()
                    .title()
                    .vendor()
                    .productType()
                    .status()
                        .parent()
                    .tags()
                    .hasOnlyDefaultVariant()
                    .variants(250, null, null, null, null, null)
                        .edges()
                            .node()
                                .id()
                                .title()
                                .price()
                                .sku()
                                .barcode()
                                .selectedOptions()
                                    .name()
                                    .value()
                                    .parent()
                                .parent()
                            .parent()
                        .pageInfo()
                            .hasNextPage()
                            .parent()
                        .parent()
                    .options()
                        .id()
                        .name()
                        .values()
                        .parent()
                    .metafields(null, null, 100, null, null, null, null)
                        .edges()
                            .node()
                                .id()
                                .namespace()
                                .key()
                                .value()
                                .type()
                                .parent()
                            .parent()
                        .pageInfo()
                            .hasNextPage()
                            .endCursor()
                            .parent()
                        .parent()
                    .parent()
                .parent()
            .pageInfo()
                .hasNextPage()
                .endCursor()
        // @formatter:on

        val request = GraphQLQueryRequest(query, root)
        val response = apiClient.executeQuery(request.serialize())
        require(!response.hasErrors()) { "Product collections find failed: " + response.errors }
        val payload = response.extractValueAsObject("products", ProductConnection::class.java)
        return Pair(
            payload.edges.asSequence()
                .map { it.node }
                .map { ShopifyProduct(it) },
            payload.pageInfo
        )
    }
}
