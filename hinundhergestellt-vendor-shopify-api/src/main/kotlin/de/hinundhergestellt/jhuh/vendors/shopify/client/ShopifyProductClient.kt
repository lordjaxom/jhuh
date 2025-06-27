@file:OptIn(ExperimentalCoroutinesApi::class)

package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildQuery
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.client.ProductProjection
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MediaEdge
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.PageInfo
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Product
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductConnection
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductCreatePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductDeletePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductEdge
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductUpdatePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantEdge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("shopify.read-only", havingValue = "false", matchIfMissing = true)
class ShopifyProductClient(
    private val shopifyGraphQLClient: WebClientGraphQLClient
) {
    fun fetchAll() = pageAll { fetchNextPage(it) }.map { it.toShopifyProduct() }

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
            .toList()
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

    private suspend fun fetchNextPage(after: String?): Pair<List<ProductEdge>, PageInfo> {
        val request = buildQuery {
            products(first = 50, after = after) {
                edges {
                    node {
                        handle; id; title; vendor; productType; status; tags; hasOnlyDefaultVariant; descriptionHtml
                        variants()
                        options { id; name; values }
                        metafields(first = 50) {
                            edges {
                                node { id; namespace; key; value; type }
                            }
                            pageInfo { hasNextPage }
                        }
                        media()
                    }
                }
                pageInfo { hasNextPage; endCursor }
            }
        }

        val payload = shopifyGraphQLClient.executeQuery<ProductConnection>(request)
        return Pair(payload.edges, payload.pageInfo)
    }

    private suspend fun fetchNextVariants(productId: String, after: String?): Pair<List<ProductVariantEdge>, PageInfo> {
        val request = buildQuery { product(id = productId) { variants(after) } }
        val payload = shopifyGraphQLClient.executeQuery<Product>(request)
        return Pair(payload.variants.edges, payload.variants.pageInfo)
    }

    private suspend fun fetchNextMedia(productId: String, after: String?): Pair<List<MediaEdge>, PageInfo> {
        val request = buildQuery { product(id = productId) { media(after) } }
        val payload = shopifyGraphQLClient.executeQuery<Product>(request)
        return Pair(payload.media.edges, payload.media.pageInfo)
    }

    private fun ProductProjection.variants(after: String? = null) =
        variants(first = 100, after = after) {
            edges {
                node {
                    id; title; price; sku; barcode
                    inventoryItem {
                        measurement {
                            weight { unit; value }
                        }
                    }
                    selectedOptions { name; value }
                    media(first = 2) {
                        edges {
                            node {
                                onMediaImage { id }
                            }
                        }
                    }
                }
            }
            pageInfo { hasNextPage; endCursor }
        }

    private fun ProductProjection.media(after: String? = null) =
        media(first = 100, after = after) {
            edges {
                node {
                    onMediaImage {
                        id
                        image { id; src }
                    }
                }
            }
            pageInfo { hasNextPage; endCursor }
        }

    private suspend fun ProductEdge.toShopifyProduct() = ShopifyProduct(node, node.toShopifyProductVariants(), node.toShopifyMedia())

    private suspend fun Product.toShopifyProductVariants() =
        flowOf(variants.edges.asFlow(), pageAll(variants.pageInfo) { fetchNextVariants(id, it) })
            .flattenConcat()
            .map { ShopifyProductVariant(it.node) }
            .toList()

    private suspend fun Product.toShopifyMedia() =
        flowOf(media.edges.asFlow(), pageAll(media.pageInfo) { fetchNextMedia(id, it) })
            .flattenConcat()
            .map { ShopifyMedia(it.node) }
            .toList()
}

