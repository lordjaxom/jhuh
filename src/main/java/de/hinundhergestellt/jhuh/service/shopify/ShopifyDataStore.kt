package de.hinundhergestellt.jhuh.service.shopify

import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.Scope
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.stereotype.Service
import java.util.concurrent.Callable
import kotlin.streams.asSequence

private val logger = KotlinLogging.logger {  }

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
class ShopifyDataStore(
    factory: ShopifyDataStoreFactory
) {
    val products by factory.products()
}

@Service
class ShopifyDataStoreFactory(
    private val productClient: ShopifyProductClient,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: AsyncTaskExecutor
) {
    fun products(): Lazy<List<ShopifyProduct>> {
        val products = taskExecutor.submit(Callable { logger.info{"Loading shprod"}; productClient.findAll().asSequence().toList() })
        return lazy { products.get() }
    }
}
