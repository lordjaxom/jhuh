package de.hinundhergestellt.jhuh.backend.autogenerate

import de.hinundhergestellt.jhuh.HuhProperties
import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncTechnicalDetail
import de.hinundhergestellt.jhuh.backend.syncdb.update
import de.hinundhergestellt.jhuh.tools.productNameForImages
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.moveTo

private var logger = KotlinLogging.logger { }

@Service
class AutoGenerateService(
    private val mappingService: MappingService,
    private val shopTexterService: ShopTexterService,
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val syncProductRepository: SyncProductRepository,
    private val properties: HuhProperties
) {
    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS)
    fun autoGenerateProductAttributes() {
        syncProductRepository.findAllByGenerateTextsIsTrueAndArtooIdIsNotNull().asSequence()
            .mapNotNull { sync -> artooDataStore.findProductById(sync.artooId!!)?.let { sync to it } }
            .filter { (sync, artoo) -> mappingService.checkForProblems(artoo, sync).none { it.error } }
            .forEach { (sync, artoo) -> autoGenerateProductAttributes(sync, artoo) }
    }

    private fun autoGenerateProductAttributes(sync: SyncProduct, artoo: ArtooMappedProduct) {
        syncProductRepository.update(sync.id) { generateTexts = false }

        try {
            generateTextsAndDetails(sync, artoo)
            reworkProductTexts(sync, artoo)

            syncProductRepository.update(sync.id) {
                descriptionHtml = sync.descriptionHtml
                seoTitle = sync.seoTitle
                metaDescription = sync.metaDescription
                urlHandle = sync.urlHandle
                tags = sync.tags

                val newTechnicalDetails = sync.technicalDetails.toList() // create copy just to be sure
                technicalDetails.clear()
                technicalDetails += newTechnicalDetails
            }
        } catch (e: AutoGenerateException) {
            logger.error { "Error generating texts for ${artoo.description}: ${e.message}" }
        }
    }

    private fun generateTextsAndDetails(sync: SyncProduct, artoo: ArtooMappedProduct) {
        if (sync.descriptionHtml.isNullOrEmpty() || sync.seoTitle.isNullOrEmpty() || sync.metaDescription.isNullOrEmpty()) {
            logger.info { "Generating product texts for ${artoo.description}" }

            val texts = shopTexterService.generateProductTexts(artoo, sync)
            if (sync.descriptionHtml.isNullOrEmpty()) sync.descriptionHtml = texts.descriptionHtml
            if (sync.seoTitle.isNullOrEmpty()) sync.seoTitle = texts.seoTitle
            if (sync.metaDescription.isNullOrEmpty()) sync.metaDescription = texts.metaDescription
        }
        if (sync.tags.isEmpty() || sync.technicalDetails.isEmpty()) {
            logger.info { "Generating product attributes for ${artoo.description}" }

            val details = shopTexterService.generateProductDetails(artoo, sync)
            if (sync.tags.isEmpty()) sync.tags += details.tags.toSet() - mappingService.inheritedTags(sync, artoo)
            if (sync.technicalDetails.isEmpty()) sync.technicalDetails += details.technicalDetails.toSyncTechnicalDetails()
        }
    }

    private fun reworkProductTexts(sync: SyncProduct, artoo: ArtooMappedProduct) {
        logger.info { "Reworking SEO texts for product ${artoo.description}" }

        val reworked = shopTexterService.reworkProductTexts(artoo, sync, "nonbrand")

        val newImageFolder = properties.imageDirectory
            .resolve(sync.vendor!!.name)
            .resolve(reworked.title.productNameForImages)
        if (reworked.title != artoo.description && newImageFolder.exists()) {
            throw IllegalStateException("Target folder '$newImageFolder' already exists")
        }
        if (sync.shopifyId != null && shopifyDataStore.products.any { it.handle == reworked.handle && it.id != sync.shopifyId }) {
            throw IllegalStateException("Handle '${reworked.handle}' already exists for another product")
        }

        if (reworked.handle != sync.urlHandle) {
            logger.info { "Handle: ${sync.urlHandle} to ${reworked.handle}" }
            sync.urlHandle = reworked.handle
        }
        if (reworked.title != artoo.description) {
            val oldFolder = properties.imageDirectory
                .resolve(sync.vendor!!.name)
                .resolve(artoo.description.productNameForImages)
            if (oldFolder.exists() && oldFolder != newImageFolder) {
                logger.info { "Renaming image folder: $oldFolder to $newImageFolder" }
                oldFolder.moveTo(newImageFolder)
            }

            logger.info { "Updating Artoo product name: ${artoo.description} to ${reworked.title}" }
            artoo.description = reworked.title
            runBlocking { artooDataStore.update(artoo) }
        }
        if (reworked.seoTitle != sync.seoTitle) {
            println("SEO Title: ${sync.seoTitle} to ${reworked.seoTitle}")
            sync.seoTitle = reworked.seoTitle
        }
        if (reworked.seoDescription != sync.metaDescription) {
            println("SEO Description: ${sync.metaDescription} to ${reworked.seoDescription}")
            sync.metaDescription = reworked.seoDescription
        }
        if (reworked.descriptionHtml != sync.descriptionHtml) {
            sync.descriptionHtml = reworked.descriptionHtml
        }
    }
}

private fun Map<String, String>.toSyncTechnicalDetails() =
    asSequence().mapIndexed { index, entry -> SyncTechnicalDetail(entry.key, entry.value, index) }

private class AutoGenerateException(message: String) : RuntimeException(message)