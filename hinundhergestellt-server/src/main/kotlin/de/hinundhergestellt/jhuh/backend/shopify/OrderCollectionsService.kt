package de.hinundhergestellt.jhuh.backend.shopify

import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyCollectionClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyOrderClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Collection
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.CollectionSortOrder
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MoveInput
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

@Service
class OrderCollectionsService(
    private val shopifyDataStore: ShopifyDataStore,
    private val orderClient: ShopifyOrderClient,
    private val collectionClient: ShopifyCollectionClient
) {
    @Scheduled(initialDelay = 15, fixedRate = 300, timeUnit = TimeUnit.SECONDS)
    suspend fun reorderCollections() {
        val ordersByProduct = orderClient.fetchAll().asSequence()
            .flatMap { order -> order.lineItems.edges.asSequence().map { it.node } }
            .groupingBy { it.product!!.id }
            .fold(0) { acc, lineItem -> acc + lineItem.quantity }

        collectionClient.fetchAll().asSequence()
            .filter { it.sortOrder == CollectionSortOrder.MANUAL }
            .forEach { sortCollection(it, ordersByProduct) }
    }

    private suspend fun sortCollection(collection: Collection, ordersByProduct: Map<String, Int>) {
        val sourceProducts = collection.products.edges.map { it.node.id }
        val targetProducts = sourceProducts.asSequence()
            .map { shopifyDataStore.findProductById(it)!! }
            .sortedWith(
                compareBy(
                    { soldOutSortKey(it) },
                    { onSaleSortKey(it) },
                    { soldCountSortKey(it, ordersByProduct) },
                    { it.createdAt },
                )
            )
            .map { it.id }
            .toList()
        val moves = computeMoves(sourceProducts, targetProducts)
        if (moves.isNotEmpty()) {
            logger.info { "Reordering collection '${collection.title}' with ${moves.size} moves" }
            collectionClient.reorder(collection, moves)
        }
    }

    private fun computeMoves(original: List<String>, target: List<String>): List<MoveInput> {
        val current = original.toMutableList()
        val moves = mutableListOf<MoveInput>()

        for (targetIndex in target.indices) {
            val targetId = target[targetIndex]
            val currentIndex = current.indexOf(targetId)
            if (currentIndex != targetIndex) {
                val p = current.removeAt(currentIndex)
                current.add(targetIndex, p)
                moves += MoveInput(p, "$targetIndex")
            }
        }
        return moves
    }

    private fun soldOutSortKey(product: ShopifyProduct) =
        if (product.variants.sumOf { it.inventoryQuantity } > 0) 0 else 1

    private fun onSaleSortKey(product: ShopifyProduct) =
        if (product.variants.any { variant -> variant.compareAtPrice?.let { it > variant.price } == true }) 0 else 1

    private fun soldCountSortKey(product: ShopifyProduct, ordersByProduct: Map<String, Int>) =
        -(ordersByProduct[product.id] ?: 0)
}