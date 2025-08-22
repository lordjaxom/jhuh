package de.hinundhergestellt.jhuh.backend.shoptexter.model

import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct

object ProductMapper {

    fun map(artoo: ArtooMappedProduct, sync: SyncProduct) =
        Product(
            name = artoo.name,
            title = artoo.description,
            description = sync.descriptionHtml ?: "",
            productType = sync.type ?: "",
            vendor = sync.vendor?.name ?: "",
            tags = sync.tags,
            technicalDetails = sync.technicalDetails.associate { it.name to it.value },
            hasOnlyDefaultVariant = artoo.hasOnlyDefaultVariant,
            variants = artoo.variations.map { it.name }.filter { it.isNotEmpty() },
        )
}