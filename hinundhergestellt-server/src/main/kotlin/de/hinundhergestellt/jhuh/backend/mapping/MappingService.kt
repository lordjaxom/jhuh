package de.hinundhergestellt.jhuh.backend.mapping

import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendorRepository
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.streams.asSequence

private val INVALID_TAG_CHARACTERS = """[^A-ZÄÖÜa-zäöüß0-9\\._ -]""".toRegex()

@Service
class MappingService(
    private val artooDataStore: ArtooDataStore,
    private val syncCategoryRepository: SyncCategoryRepository
) {
    @Transactional
    fun inheritedTags(syncProduct: SyncProduct, artooProduct: ArtooMappedProduct? = null): Set<String> {
        val categoryTags = (artooProduct ?: syncProduct.artooId?.let { artooDataStore.findProductById(it) })
            ?.let { artooDataStore.findCategoriesByProduct(it).map { category -> category.id }.toList() }
            ?.let { syncCategoryRepository.findByArtooIdIn(it).asSequence().flatMap { category -> category.tags } }
            ?: sequenceOf()
        val vendorTypeTags = sequenceOf(syncProduct.vendor?.name, syncProduct.type)
            .filterNotNull()
            .map { it.replace(INVALID_TAG_CHARACTERS, "") }
        return (categoryTags + vendorTypeTags).toSet()
    }
}