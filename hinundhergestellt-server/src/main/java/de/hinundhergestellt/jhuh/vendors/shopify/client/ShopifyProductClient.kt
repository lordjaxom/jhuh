package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildQuery
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.PageInfo
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductConnection
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductCreatePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductDeletePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductUpdatePayload
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component

@Component
class ShopifyProductClient(
    private val shopifyGraphQLClient: WebClientGraphQLClient
) {
    fun findAll() = pageAll {
        findAll(it)
    }

    suspend fun create(product: UnsavedShopifyProduct): ShopifyProduct {
        val request = buildMutation {
            productCreate(product.toProductCreateInput()) {
                product {
                    id
                    options { id }
                }
                userErrors { message; field }
            }
        }

        val payload = shopifyGraphQLClient.executeMutation(request, ProductCreatePayload::userErrors)
        val options = product.options.asSequence()
            .zip(payload.product!!.options.asSequence())
            .map { (option, created) -> ShopifyProductOption(option, created.id) }
            .toMutableList()
        return ShopifyProduct(product, payload.product!!.id, options)
    }

    suspend fun update(product: ShopifyProduct) {
        val request = buildMutation {
            productUpdate(product.toProductUpdateInput()) {
                userErrors { message; field }
            }
        }

        shopifyGraphQLClient.executeMutation(request, ProductUpdatePayload::userErrors)
    }

    suspend fun delete(product: ShopifyProduct) {
        val request = buildMutation {
            productDelete(product.toProductDeleteInput()) {
                userErrors { message; field }
            }
        }

        shopifyGraphQLClient.executeMutation(request, ProductDeletePayload::userErrors)
    }

    private suspend fun findAll(after: String?): Pair<List<ShopifyProduct>, PageInfo> {
        val request = buildQuery {
            products(first = 250, after = after) {
                edges {
                    node {
                        handle; id; title; vendor; productType; status; tags; hasOnlyDefaultVariant
                        variants(first = 250) {
                            edges {
                                node {
                                    id; title; price; sku; barcode
                                    selectedOptions { name; value }
                                }
                            }
                            pageInfo { hasNextPage }
                        }
                        options { id; name; values }
                        metafields(first = 100) {
                            edges {
                                node { id;namespace;key;value;type }
                            }
                            pageInfo { hasNextPage }
                        }
                    }
                }
                pageInfo { hasNextPage; endCursor }
            }
        }

        val response = shopifyGraphQLClient.reactiveExecuteQuery(request).awaitSingle()
        require(!response.hasErrors()) { "Query products failed: " + response.errors }
        val payload = response.extractValueAsObject("products", ProductConnection::class.java)
        return Pair(
            payload.edges.map { ShopifyProduct(it.node) },
            payload.pageInfo
        )
    }
}
