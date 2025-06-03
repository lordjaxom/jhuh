package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductClient
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroupClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.Scope
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.stereotype.Service
import java.util.concurrent.Callable

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
class ArtooDataStore(
    factory: ArtooDataStoreFactory
) {
    val rootCategories by factory.rootCategories()

    fun findAllProducts() =
        rootCategories.asSequence().flatMap { it.findAllProducts() }

    fun findProductById(id: Int) =
        rootCategories.firstNotNullOfOrNull { it.findProductById(id) }

    fun findParentCategoriesByProduct(product: ArtooMappedProduct) =
        rootCategories.asSequence().flatMap { it.findAllCategoriesByProduct(product) }
}

@Service
class ArtooDataStoreFactory(
    private val productGroupClient: ArtooProductGroupClient,
    private val productClient: ArtooProductClient,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: AsyncTaskExecutor
) {
    fun rootCategories(): Lazy<List<ArtooMappedCategory>> {
        val groups = taskExecutor.submit(Callable { productGroupClient.findAll().toList() })
        val products = taskExecutor.submit(Callable { productClient.findAll().toList() })
        return lazy { DataStoreBuilder(groups.get(), products.get()).rootCategories() }
    }
}

private class DataStoreBuilder(
    private val groups: List<ArtooProductGroup>,
    private val products: List<ArtooProduct>
) {
    fun rootCategories() =
        groups.asSequence()
            .filter { it.parent == 0 && it.typeId == 7 }
            .map { toCategory(it) }
            .toList()

    private fun toCategory(group: ArtooProductGroup): ArtooMappedCategory {
        val children = groups.asSequence()
            .filter { it.parent == group.id && it.typeId == 7 }
            .map { toCategory(it) }
            .toList()
        val products = products.asSequence()
            .filter { it.productGroupId == group.id && it.baseId == 0 }
            .map { toProduct(it) }
            .toList()
        return ArtooMappedCategory(group, children, products)
    }

    private fun toProduct(product: ArtooProduct) : ArtooMappedProduct {
        val variations = products.asSequence()
            .filter { it.baseId == product.id }
            .map { ArtooMappedVariation(product, it) }
            .toList()
        return ArtooMappedProduct(product, variations)
    }
}
