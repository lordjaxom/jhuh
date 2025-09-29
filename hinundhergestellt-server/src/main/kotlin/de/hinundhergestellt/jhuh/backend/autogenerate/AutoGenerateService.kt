package de.hinundhergestellt.jhuh.backend.autogenerate

import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncTechnicalDetail
import de.hinundhergestellt.jhuh.backend.syncdb.update
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.util.concurrent.TimeUnit

private var logger = KotlinLogging.logger { }

@Service
class AutoGenerateService(
    private val mappingService: MappingService,
    private val shopTexterService: ShopTexterService,
    private val artooDataStore: ArtooDataStore,
    private val syncProductRepository: SyncProductRepository,
    private val transactionOperations: TransactionOperations
) {
    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS)
    fun autoGenerateMissingProductAttributes() {
        syncProductRepository.findAllBySyncedIsFalseAndArtooIdIsNotNull().asSequence()
            .filter { hasMissingProductAttributes(it) }
            .mapNotNull { sync -> artooDataStore.findProductById(sync.artooId!!)?.let { sync to it } }
            .filter { (sync, artoo) -> mappingService.checkForProblems(artoo, sync).none { it.error } }
            .forEach { (sync, artoo) -> autoGenerateMissingProductAttributes(sync, artoo) }
    }

    private fun hasMissingProductAttributes(sync: SyncProduct) =
        sync.descriptionHtml.isNullOrEmpty() || sync.tags.isEmpty() || sync.technicalDetails.isEmpty()

    private fun autoGenerateMissingProductAttributes(sync: SyncProduct, artoo: ArtooMappedProduct) {
        logger.info { "Generating missing product attributes for ${artoo.description}" }

        val updates = mutableListOf<SyncProduct.() -> Unit>()

        if (sync.descriptionHtml.isNullOrEmpty() || sync.seoTitle.isNullOrEmpty() || sync.metaDescription.isNullOrEmpty()) {
            val texts = shopTexterService.generateProductTexts(artoo, sync, null)
            if (sync.descriptionHtml.isNullOrEmpty()) updates += { descriptionHtml = texts.descriptionHtml }
            if (sync.seoTitle.isNullOrEmpty()) updates += { seoTitle = texts.seoTitle }
            if (sync.metaDescription.isNullOrEmpty()) updates += { metaDescription = texts.metaDescription }
        }
        if (sync.tags.isEmpty() || sync.technicalDetails.isEmpty()) {
            val details = shopTexterService.generateProductDetails(artoo, sync, null)
            if (sync.tags.isEmpty()) updates += { tags += details.tags.toSet() - mappingService.inheritedTags(sync, artoo) }
            if (sync.technicalDetails.isEmpty()) updates += { technicalDetails += details.technicalDetails.toSyncTechnicalDetails() }
        }

        transactionOperations.execute {
            syncProductRepository.update(sync.id) { updates.forEach { it() } }
        }
    }
}

private fun Map<String, String>.toSyncTechnicalDetails() =
    asSequence().mapIndexed { index, entry -> SyncTechnicalDetail(entry.key, entry.value, index) }