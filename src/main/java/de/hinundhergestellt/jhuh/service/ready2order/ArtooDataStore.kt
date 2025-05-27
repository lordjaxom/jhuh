package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductClient
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroupClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.Scope
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.stereotype.Service
import java.util.concurrent.Callable

private val logger = KotlinLogging.logger {  }

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
class ArtooDataStore(
    factory: ArtooDataStoreFactory
) {
    val rootCategories by factory.rootCategories()
}

@Service
class ArtooDataStoreFactory(
    private val productGroupClient: ArtooProductGroupClient,
    private val productClient: ArtooProductClient,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: AsyncTaskExecutor
) {
    fun rootCategories(): Lazy<List<ArtooMappedCategory>> {
        val groups = taskExecutor.submit(Callable { logger.info { "Loading products" }; productGroupClient.findAll().toList() })
        val products = taskExecutor.submit(Callable { logger.info { "Loading groups" }; productClient.findAll().toList() })
        return lazy { DataStoreBuilder(groups.get(), products.get()).rootCategories() }
    }
}

private class DataStoreBuilder(
    private val groups: List<ArtooProductGroup>,
    private val products: List<ArtooProduct>
) {

    fun rootCategories() =
        groups.asSequence()
            .filter { it.parent == 0 }
            .map { toCategory(it) }
            .toList()

    private fun toCategory(group: ArtooProductGroup): ArtooMappedCategory {
        val children = groups.asSequence()
            .filter { it.typeId == 7 }
            .filter { it.parent == group.id }
            .map { toCategory(it) }
            .toList()

        val productsWithVariations = groups.asSequence()
            .filter { it.typeId == 3 }
            .filter { it.parent == group.id }
            .map { toProduct(it) }
        val singleProducts = products.asSequence()
            .filter { it.productGroupId == group.id }
            .map { SingleArtooMappedProduct(it) }
        val products = (productsWithVariations + singleProducts).toList()

        return ArtooMappedCategory(group, children, products)
    }

    private fun toProduct(group: ArtooProductGroup): ArtooMappedProduct {
        val variations = products.asSequence()
            .filter { it.productGroupId == group.id }
            .map { ArtooMappedVariation(it) }
            .toList()

        return GroupArtooMappedProduct(group, variations)
    }
}
