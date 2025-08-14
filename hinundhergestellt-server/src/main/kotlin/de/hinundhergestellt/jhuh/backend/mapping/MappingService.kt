package de.hinundhergestellt.jhuh.backend.mapping

import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.streams.asSequence

private const val METAFIELD_NAMESPACE = "custom"
private val INVALID_TAG_CHARACTERS = """[^A-ZÄÖÜa-zäöüß0-9\\._ -]""".toRegex()

@Service
class MappingService(
    private val artooDataStore: ArtooDataStore,
    private val syncCategoryRepository: SyncCategoryRepository
) {
    @Transactional
    fun inheritedTags(sync: SyncProduct, artoo: ArtooMappedProduct? = null): Set<String> {
        val categoryTags = (artoo ?: sync.artooId?.let { artooDataStore.findProductById(it) })
            ?.let { artooDataStore.findCategoriesByProduct(it).map { category -> category.id }.toList() }
            ?.let { syncCategoryRepository.findByArtooIdIn(it).asSequence().flatMap { category -> category.tags } }
            ?: sequenceOf()
        val vendorTypeTags = sequenceOf(sync.vendor?.name, sync.type)
            .filterNotNull()
            .map { sanitizeTag(it) }
        return (categoryTags + vendorTypeTags).toSet()
    }

    @Transactional
    fun allTags(syncProduct: SyncProduct, artoo: ArtooMappedProduct? = null): Set<String> {
        return inheritedTags(syncProduct, artoo) + syncProduct.tags
    }

    fun sanitizeTag(tag: String) = tag.replace(INVALID_TAG_CHARACTERS, "")

    fun customMetafields(syncProduct: SyncProduct) =
        mutableListOf(
            metafield("vendor_address", syncProduct.vendor!!.address!!, ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD),
            metafield("vendor_email", syncProduct.vendor!!.email!!, ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD),
            metafield("product_specs", technicalDetails(syncProduct), ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD)
        )

    fun checkForProblems(artoo: ArtooMappedProduct, sync: SyncProduct) = buildList {
        if (artoo.description.isEmpty()) add(MappingProblem("Produkt hat keine Beschreibung (Titel in Shopify)", true))

        if (artoo.barcodes.isEmpty())
            add(MappingProblem(if (artoo.hasOnlyDefaultVariant) "Produkt hat keinen Barcode" else "Produkt hat keine Barcodes", true))
        else if (artoo.barcodes.size < artoo.variations.size)
            add(MappingProblem("Nicht alle Variationen haben einen Barcode", false))

        if (artoo.variations.groupingBy { it.name }.eachCount().any { (_, count) -> count > 1 })
            add(MappingProblem("Produkt hat Variationen mit gleichem Namen", true))
        if (!artoo.hasOnlyDefaultVariant && artoo.variations.any { it.name.isEmpty() })
            add(MappingProblem("Nicht alle Variationen haben einen Namen", false))
        if (artoo.hasOnlyDefaultVariant && !sync.variants.firstOrNull().hasWeight())
            add(MappingProblem("Produkt hat keine Gewichtsangabe", true))

        sync.vendor.also {
            if (it == null) add(MappingProblem("Produkt hat keinen Hersteller", true))
            else if (it.email == null || it.address == null) add(MappingProblem("Herstellerangaben unvollständig", true))
        }

        if (sync.type == null) add(MappingProblem("Produkt hat keine Produktart", true))
    }

    fun checkForProblems(artoo: ArtooMappedVariation, sync: SyncVariant, product: ArtooMappedProduct) = buildList {
        if (artoo.barcode == null) add(MappingProblem("Variation hat keinen Barcode", true))
        if (artoo.name.isEmpty()) add(MappingProblem("Variation hat keinen Namen", true))
        else if (artoo.name.startsWith(product.name, ignoreCase = true))
            add(MappingProblem("Variationsname beginnt mit Produktnamen", true))
        if (!sync.hasWeight()) add(MappingProblem("Variation hat keine Gewichtsangabe", true))
    }

    private fun technicalDetails(syncProduct: SyncProduct) =
        syncProduct.technicalDetails.joinToString(
            separator = "",
            prefix = "<table>",
            postfix = "</table>",
            transform = { "<tr><th>${it.name}</th><td>${it.value}</td></tr>" }
        )
}

private fun metafield(key: String, value: String, type: ShopifyMetafieldType) = ShopifyMetafield(METAFIELD_NAMESPACE, key, value, type)

private fun SyncVariant?.hasWeight() = this?.weight?.run { compareTo(BigDecimal.ZERO) != 0 } ?: false