package de.hinundhergestellt.jhuh.vendors.ready2order.datastore

import de.hinundhergestellt.jhuh.util.deferredWithRefresh
import de.hinundhergestellt.jhuh.util.ifDirty
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroup
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.util.concurrent.CopyOnWriteArrayList

@Service
class ArtooDataStore(
    private val productGroupClient: ArtooProductGroupClient,
    private val productClient: ArtooProductClient,
    private val applicationCoroutineScope: CoroutineScope
) {
    private val rootCategoriesDeferred = deferredWithRefresh(applicationCoroutineScope) { fetchRootCategories() }
    val rootCategories by rootCategoriesDeferred

    val refreshListeners = CopyOnWriteArrayList<() -> Unit>()

    fun findAllProducts() =
        rootCategories.asSequence().flatMap { it.findAllProducts() }

    fun findProductById(id: String) =
        rootCategories.firstNotNullOfOrNull { it.findProductById(id) }

    fun findCategoriesByProduct(product: ArtooMappedProduct) =
        rootCategories.asSequence().flatMap { it.findCategoriesByProduct(product) }

    suspend fun update(product: ArtooMappedProduct) {
        when (product) {
            is ArtooMappedProduct.Single ->
                product.product.ifDirty { productClient.update(it) }

            is ArtooMappedProduct.Group -> {
                product.group.ifDirty { productGroupClient.update(it) }
                product.variations.forEach { variation -> variation.product.ifDirty { productClient.update(it) } }
            }
        }
    }

    // @Scheduled(initialDelay = 15, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    fun refresh() {
        applicationCoroutineScope.async {
            rootCategoriesDeferred.refreshAndAwait()
            refreshListeners.forEach { it() }
        }
    }

    private suspend fun fetchRootCategories() = coroutineScope {
        val groups = async { productGroupClient.findAll().toList() }
        val products = productClient.findAll().toList()
        CategoriesAndProductsBuilder(groups.await(), products).build()
    }
}

private class CategoriesAndProductsBuilder(
    private val groups: List<ArtooProductGroup>,
    private val products: List<ArtooProduct>
) {
    fun build() =
        groups.asSequence()
            .filter { it.parent == null && it.type == ArtooProductGroupType.STANDARD }
            .map { it.toCategory() }
            .sortedBy { it.name }
            .toList()

    private fun ArtooProductGroup.toCategory(): ArtooMappedCategory {
        val children = groups.asSequence()
            .filter { it.parent == id && it.type == ArtooProductGroupType.STANDARD }
            .map { it.toCategory() }
            .sortedBy { it.name }
            .toList()

        val productsWithVariations = groups.asSequence()
            .filter { it.parent == id && it.type == ArtooProductGroupType.VARIANTS }
            .map { it.toProduct() }
        val singleProducts = products.asSequence()
            .filter { it.productGroupId == id }
            .map { ArtooMappedProduct.Single(it) }
        val products = (productsWithVariations + singleProducts).sortedBy { it.name }.toList()

        return ArtooMappedCategory(this, children, products)
    }

    private fun ArtooProductGroup.toProduct(): ArtooMappedProduct {
        val variations = products.asSequence()
            .filter { it.productGroupId == id }
            .map { ArtooMappedVariation(it, false) }
            .sortedBy { it.name }
            .toList()
        return ArtooMappedProduct.Group(this, variations)
    }
}
