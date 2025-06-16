package de.hinundhergestellt.jhuh.vendors.ready2order.datastore

import de.hinundhergestellt.jhuh.util.asyncWithRefresh
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroup
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.stereotype.Service
import java.util.concurrent.Callable

private val logger = KotlinLogging.logger {}

@Service
class ArtooDataStore(
    private val productGroupClient: ArtooProductGroupClient,
    private val productClient: ArtooProductClient
) {
    private val rootCategoriesAsync = asyncWithRefresh { fetchRootCategories() }
    val rootCategories by rootCategoriesAsync

    val stateChangeListeners by rootCategoriesAsync::stateChangeListeners

    fun findAllProducts() =
        rootCategories.asSequence().flatMap { it.findAllProducts() }

    fun findProductById(id: String) =
        rootCategories.firstNotNullOfOrNull { it.findProductById(id) }

    fun findCategoriesByProduct(product: ArtooMappedProduct) =
        rootCategories.asSequence().flatMap { it.findCategoriesByProduct(product) }

    // @Scheduled(initialDelay = 15, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    fun refresh() = rootCategoriesAsync.refresh()

    suspend fun refreshAndAwait() {
        rootCategoriesAsync.refreshAndGet()
    }

    private suspend fun fetchRootCategories(): List<ArtooMappedCategory> = coroutineScope {
        logger.info { "Fetching all root-categories in runBlocking" }
        val groups = async {
            logger.info { "Fetching groups in async" }
            val r = productGroupClient.findAll().toList()
            logger.info { "Done fetching groups in async" }
            r
        }
        val products = productClient.findAll().toList()
        logger.info { "Done fetching products, building tree" }
        CategoriesAndProductsBuilder(groups.await(), products).rootCategories
    }
}

private class CategoriesAndProductsBuilder(
    private val groups: List<ArtooProductGroup>,
    private val products: List<ArtooProduct>
) {
    val rootCategories =
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
