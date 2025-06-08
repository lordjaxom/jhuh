package de.hinundhergestellt.jhuh.vendors.ready2order.datastore

import de.hinundhergestellt.jhuh.util.asyncWithRefresh
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroup
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.stereotype.Service
import java.util.concurrent.Callable

@Service
class ArtooDataStore(
    private val productGroupClient: ArtooProductGroupClient,
    private val productClient: ArtooProductClient,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: AsyncTaskExecutor
) {
    private val rootCategoriesAsync = asyncWithRefresh(taskExecutor) { fetchRootCategories() }
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

    private fun fetchRootCategories(): List<ArtooMappedCategory> {
        val groups = taskExecutor.submit(Callable { productGroupClient.findAll().toList() })
        val products = productClient.findAll().toList()
        return CategoriesAndProductsBuilder(groups.get(), products).rootCategories
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
            .toList()

    private fun ArtooProductGroup.toCategory(): ArtooMappedCategory {
        val children = groups.asSequence()
            .filter { it.parent == id && it.type == ArtooProductGroupType.STANDARD }
            .map { it.toCategory() }
            .toList()

        val productsWithVariations = groups.asSequence()
            .filter { it.parent == id && it.type == ArtooProductGroupType.VARIANTS }
            .map { it.toProduct() }
        val singleProducts = products.asSequence()
            .filter { it.productGroupId == id }
            .map { ArtooMappedProduct.Single(it) }
        val products = (productsWithVariations + singleProducts).toList()

        return ArtooMappedCategory(this, children, products)
    }

    private fun ArtooProductGroup.toProduct(): ArtooMappedProduct {
        val variations = products.asSequence()
            .filter { it.productGroupId == id }
            .map { ArtooMappedVariation(it) }
            .toList()
        return ArtooMappedProduct.Group(this, variations)
    }
}
