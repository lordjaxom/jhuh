@file:OptIn(ExperimentalCoroutinesApi::class)

package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildQuery
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.client.MetaobjectDefinitionProjection
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectCreatePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectDefinition
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectDefinitionConnection
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectEdge
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectUpdatePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.PageInfo
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
class ShopifyMetaobjectClient(
    private val shopifyGraphQLClient: WebClientGraphQLClient
) {
    suspend fun fetchAllTypes(): List<String> {
        val request = buildQuery {
            metaobjectDefinitions(first = 250) {
                edges {
                    node {
                        type
                    }
                }
                pageInfo { hasNextPage }
            }
        }

        val payload = shopifyGraphQLClient.executeQuery<MetaobjectDefinitionConnection>(request)
        require(!payload.pageInfo.hasNextPage) { "Shop has more metaobject definitions than were loaded" }
        return payload.edges.map { it.node.type }
    }

    suspend fun fetchDefinitionByType(type: String): ShopifyMetaobjectDefinition? {
        val request = buildQuery {
            metaobjectDefinitionByType(type) {
                id; name; description; this@metaobjectDefinitionByType.type
                fieldDefinitions {
                    key; name; description; required
                    type { category; name; valueType }
                }
                metaobjects()
            }
        }

        val payload = shopifyGraphQLClient.executeQuery<MetaobjectDefinition?>(request, "metaobjectDefinitionByType")
        return payload?.toShopifyMetaobjectDefinition()
    }

    suspend fun create(metaobject: UnsavedShopifyMetaobject): ShopifyMetaobject {
        val request = buildMutation {
            metaobjectCreate(metaobject.toMetaobjectCreateInput()) {
                metaobject { id }
                userErrors { message; field }
            }
        }

        val payload = shopifyGraphQLClient.executeMutation(request, MetaobjectCreatePayload::userErrors)
        return ShopifyMetaobject(metaobject, payload.metaobject!!.id)
    }

    suspend fun update(metaobject: ShopifyMetaobject) {
        val request = buildMutation {
            metaobjectUpdate(metaobject.id, metaobject.toMetaobjectUpdateInput()) {
                userErrors { message; field }
            }
        }

        shopifyGraphQLClient.executeMutation(request, MetaobjectUpdatePayload::userErrors)
    }

    private suspend fun fetchNextMetaobjects(type: String, after: String?): Pair<List<MetaobjectEdge>, PageInfo> {
        val request = buildQuery { metaobjectDefinitionByType(type) { metaobjects(after = after) } }
        val payload = shopifyGraphQLClient.executeQuery<MetaobjectDefinition>(request, "metaobjectDefinitionByType")
        return Pair(payload.metaobjects.edges, payload.metaobjects.pageInfo)
    }

    private fun MetaobjectDefinitionProjection.metaobjects(after: String? = null) =
        metaobjects(first = 250, after = after) {
            edges {
                node {
                    id; handle; displayName; type
                    fields { key; type; value; jsonValue }
                }
            }
            pageInfo { hasNextPage; endCursor }
        }

    private suspend fun MetaobjectDefinition.toShopifyMetaobjectDefinition() =
        ShopifyMetaobjectDefinition(this, toShopifyMetaobjects())

    private suspend fun MetaobjectDefinition.toShopifyMetaobjects() =
        flowOf(metaobjects.edges.asFlow(), pageAll(metaobjects.pageInfo) { fetchNextMetaobjects(type, it) })
            .flattenConcat()
            .map { ShopifyMetaobject(it.node) }
            .toList()
}