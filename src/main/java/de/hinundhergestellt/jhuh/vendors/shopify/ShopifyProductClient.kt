package de.hinundhergestellt.jhuh.vendors.shopify

import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest
import com.shopify.admin.client.ProductCreateGraphQLQuery
import com.shopify.admin.client.ProductCreateProjectionRoot
import com.shopify.admin.client.ProductDeleteGraphQLQuery
import com.shopify.admin.client.ProductDeleteProjectionRoot
import com.shopify.admin.client.ProductVariantsBulkCreateGraphQLQuery
import com.shopify.admin.client.ProductVariantsBulkCreateProjectionRoot
import com.shopify.admin.client.ProductVariantsBulkDeleteGraphQLQuery
import com.shopify.admin.client.ProductVariantsBulkDeleteProjectionRoot
import com.shopify.admin.client.ProductsGraphQLQuery
import com.shopify.admin.client.ProductsProjectionRoot
import com.shopify.admin.types.*
import org.springframework.stereotype.Component

@Component
class ShopifyProductClient(
    private val apiClient: GraphQLClient
) {
    fun findAll() = pageAll {
        findAll(it)
    }

    fun save(product: Product) {
        require(product.id == null) { "Product id must be null" }

        val productInput = ProductCreateInput()
        productInput.title = product.title
        productInput.vendor = product.vendor
        productInput.productType = product.productType
        productInput.tags = product.tags

        val query = ProductCreateGraphQLQuery.newRequest()
            .product(productInput)
            .build()

        // @formatter:off
        val root = ProductCreateProjectionRoot<BaseSubProjectionNode<*, *>?, BaseSubProjectionNode<*, *>?>()
        .product()
        .id()
        .parent()
        .userErrors()
        .message()
        .field()
        // @formatter:on

        val request = GraphQLQueryRequest(query, root)
        val response = apiClient.executeQuery(request.serialize())
        val payload = response.extractValueAsObject("productCreate", ProductCreatePayload::class.java)
        require(payload.userErrors.isEmpty()) { "Product creation failed: " + payload.userErrors }
        product.id = payload.product.id
    }

    fun deleteProduct(product: ShopifyProduct) {
        val input = ProductDeleteInput()
        input.id = product.id

        val query = ProductDeleteGraphQLQuery.newRequest()
            .input(input)
            .build()

        // @formatter:off
        val root = ProductDeleteProjectionRoot<BaseSubProjectionNode<*, *>, BaseSubProjectionNode<*, *>>()
            .userErrors()
                .message()
                .field()
        // @formatter:on

        val request = GraphQLQueryRequest(query, root)
        val response = apiClient.executeQuery(request.serialize())
        val payload = response.extractValueAsObject("productDelete", ProductDeletePayload::class.java)
        require(payload.userErrors.isEmpty()) { "Product delete failed: " + payload.userErrors }
    }

    fun saveVariants(product: ShopifyProduct, variants: List<ShopifyVariant>) {
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
        require(response.errors.isEmpty()) { "Product variants bulk create failed: " + response.errors }
        val payload = response.extractValueAsObject("productVariantsBulkCreate", ProductVariantsBulkCreatePayload::class.java)
        require((payload.userErrors.isEmpty())) { "Product variants bulk create failed: " + payload.userErrors }
    }

    fun deleteVariants(product: ShopifyProduct, variants: List<ShopifyVariant>) {
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
        val payload = response.extractValueAsObject("productVariantsBulkDelete", ProductVariantsBulkDeletePayload::class.java)
        require(payload.userErrors.isEmpty()) { "Product creation failed: " + payload.userErrors }
    }

    private fun findAll(after: String?): Pair<Sequence<ShopifyProduct>, PageInfo> {
        val query = ProductsGraphQLQuery.newRequest()
            .first(100)
            .after(after)
            .build()

        // @formatter:off
        val root = ProductsProjectionRoot<BaseSubProjectionNode<*, *>?, BaseSubProjectionNode<*, *>?>()
                .edges()
                    .node()
                        .handle()
                        .id()
                        .title()
                        .vendor()
                        .productType()
                        .tags()
                        .variants(100, null, null, null, null, null)
                            .edges()
                                .node()
                                    .id()
                                    .title()
                                    .price()
                                    .sku()
                                    .barcode() //.inventoryItem().id().parent()
                                    .parent()
                                .parent()
                            .pageInfo()
                                .hasNextPage()
                                .parent()
                            .parent()
                        .options()
                            .id()
                            .name()
                            .parent()
                        .parent()
                    .parent()
                .pageInfo()
                    .hasNextPage()
                    .endCursor()
        // @formatter:on

        val request = GraphQLQueryRequest(query, root)
        val response = apiClient.executeQuery(request.serialize())
        if (response.hasErrors()) {
            throw RuntimeException(response.errors.toString()) // TODO
        }
        val payload = response.extractValueAsObject("products", ProductConnection::class.java)
        return Pair(
            payload.edges.asSequence()
                .map { it.node }
                .map { ShopifyProduct(it) },
            payload.pageInfo
        )
    }
}
