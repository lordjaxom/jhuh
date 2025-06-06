package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.util.asyncWithReset
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductClient
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroupClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

@Service
class ArtooDataStore(
    private val productGroupClient: ArtooProductGroupClient,
    private val productClient: ArtooProductClient,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: AsyncTaskExecutor
) {
    private val rootCategoriesAsync = asyncWithReset(taskExecutor) { fetchRootCategories() }
    val rootCategories by rootCategoriesAsync

    val stateChangeListeners by rootCategoriesAsync::stateChangeListeners

    fun findAllProducts() =
        rootCategories.asSequence().flatMap { it.findAllProducts() }

    fun findProductById(id: Int) =
        rootCategories.firstNotNullOfOrNull { it.findProductById(id) }

    fun findCategoriesByProduct(product: ArtooMappedProduct) =
        rootCategories.asSequence().flatMap { it.findCategoriesByProduct(product) }

    @Scheduled(initialDelay = 15, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    fun refresh() = refresh(false)
    fun refresh(wait: Boolean) = rootCategoriesAsync.refresh(wait)

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
            .filter { it.parent == null && it.typeId == 7 }
            .map { it.toCategory() }
            .toList()

    private fun ArtooProductGroup.toCategory(): ArtooMappedCategory {
        val children = groups.asSequence()
            .filter { it.parent == id && it.typeId == 7 }
            .map { it.toCategory() }
            .toList()
        val products = products.asSequence()
            .filter { it.productGroupId == id && it.baseId == null }
            .map { it.toProduct() }
            .toList()
        return ArtooMappedCategory(this, children, products)
    }

    private fun ArtooProduct.toProduct(): ArtooMappedProduct {
        val variations = products.asSequence()
            .filter { it.baseId == id }
            .map { ArtooMappedVariation(this, it) }
            .toList()
        return ArtooMappedProduct(this, variations)
    }
}
