package de.hinundhergestellt.jhuh.backend.mapping

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.tools.ArtooImageTools
import de.hinundhergestellt.jhuh.tools.SyncImageTools
import de.hinundhergestellt.jhuh.tools.syncImageProductName
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedCategory
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldType
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.findById
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.streams.asSequence

private const val METAFIELD_NAMESPACE = "custom"
private val INVALID_TAG_CHARACTERS = """[^A-ZÄÖÜa-zäöüß0-9\\._ -]""".toRegex()

@Service
class MappingService(
    private val artooDataStore: ArtooDataStore,
    private val syncCategoryRepository: SyncCategoryRepository,
    private val artooImageTools: ArtooImageTools
) {
    private val objectMapper = jacksonObjectMapper()

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
    fun allTags(syncProduct: SyncProduct, artoo: ArtooMappedProduct? = null) =
        inheritedTags(syncProduct, artoo) + syncProduct.tags

    @Transactional
    fun inheritedTags(artoo: ArtooMappedCategory) =
        artooDataStore.findCategoriesByCategory(artoo).map { it.id }.toList()
            .let { syncCategoryRepository.findByArtooIdIn(it).asSequence().flatMap { category -> category.tags } }
            .toSet()

    fun sanitizeTag(tag: String) = tag.replace(INVALID_TAG_CHARACTERS, "")

    fun customMetafields(syncProduct: SyncProduct) =
        mutableListOf(
            metafield("vendor_address", syncProduct.vendor!!.address!!, ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD),
            metafield("vendor_email", syncProduct.vendor!!.email!!, ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD),
            metafield("product_specs", technicalDetails(syncProduct), ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD),
            metafield("technical_details", technicalDetailsJson(syncProduct), ShopifyMetafieldType.JSON)
        )

    fun extractTechnicalDetails(shopifyProduct: ShopifyProduct) =
        shopifyProduct.metafields.findById(METAFIELD_NAMESPACE, "technical_details")
            ?.let { objectMapper.readValue<Map<String, String>>(it.value) }
            ?: mapOf()

    fun checkForProblems(artoo: ArtooMappedProduct, sync: SyncProduct) = buildList {
        if (artoo.description.isEmpty()) add(MappingProblem("Produkt hat keine Beschreibung (Titel in Shopify)", true))

        if (artoo.barcodes.isEmpty())
            add(MappingProblem( "Produkt hat keine Barcode(s)", true))
        else if (artoo.barcodes.size < artoo.variations.size)
            add(MappingProblem("Nicht alle Variationen haben einen Barcode", false))

        if (artoo.itemNumbers.isEmpty())
            add(MappingProblem("Produkt hat keine Artikelnummer(n)", true))
        else if (artoo.itemNumbers.size < artoo.variations.size)
            add(MappingProblem("Nicht alle Variationen haben eine Artikelnummer", false))

        if (artoo.variations.groupingBy { it.name }.eachCount().any { (_, count) -> count > 1 })
            add(MappingProblem("Produkt hat Variationen mit gleichem Namen", true))
        if (!artoo.hasOnlyDefaultVariant && artoo.variations.any { it.name.isEmpty() })
            add(MappingProblem("Nicht alle Variationen haben einen Namen", false))

        if (artoo.hasOnlyDefaultVariant) {
            if (!sync.variants.firstOrNull().hasWeight()) add(MappingProblem("Produkt hat keine Gewichtsangabe", true))
            else if (!sync.variants.firstOrNull().hasValidWeight()) add(MappingProblem("Gewichtsangabe ungültig (0,5g oder >= 30g)", true))
        }

        sync.vendor.also {
            if (it == null) add(MappingProblem("Produkt hat keinen Hersteller", true))
            else if (it.email == null || it.address == null) add(MappingProblem("Herstellerangaben unvollständig", true))
        }

        if (sync.type == null) add(MappingProblem("Produkt hat keine Produktart", true))

        if (!artoo.hasOnlyDefaultVariant && sync.optionName == null) add(MappingProblem("Optionsname für Varianten fehlt", true))

        if (artooImageTools.findProductImages(artoo).isEmpty())
            add(MappingProblem("Keine Produktbilder vorhanden", false))
    }

    fun checkForProblems(artoo: ArtooMappedVariation, sync: SyncVariant) = buildList {
        if (artoo.barcode == null) add(MappingProblem("Variation hat keinen Barcode", true))
        if (artoo.itemNumber == null) add(MappingProblem("Variation hat keine Artikelnummer", true))

        if (artoo.name.isEmpty()) add(MappingProblem("Variation hat keinen Namen", true))
        else if (artoo.name.startsWith(artoo.parent.name, ignoreCase = true))
            add(MappingProblem("Variationsname beginnt mit Produktnamen", true))

        if (!sync.hasWeight()) add(MappingProblem("Variation hat keine Gewichtsangabe", true))
        else if (!sync.hasValidWeight()) add(MappingProblem("Gewichtsangabe ungültig (0,5g oder >= 30g)", true))

        if (artoo.itemNumber == null || artooImageTools.findVariantImages(artoo.parent).none { it.variantSku == artoo.itemNumber })
            add(MappingProblem("Kein Variantenbild vorhanden", false)) // TODO: true if linkedMetaField used
    }

    private fun technicalDetails(syncProduct: SyncProduct) =
        syncProduct.technicalDetails.joinToString(
            separator = "",
            prefix = "<table>",
            postfix = "</table>",
            transform = { "<tr><th>${it.name}</th><td>${it.value}</td></tr>" }
        )

    private fun technicalDetailsJson(syncProduct: SyncProduct) =
        objectMapper.writeValueAsString(syncProduct.technicalDetails.associate { it.name to it.value })
}

private fun metafield(key: String, value: String, type: ShopifyMetafieldType) = ShopifyMetafield(METAFIELD_NAMESPACE, key, value, type)

private fun SyncVariant?.hasWeight() =
    this?.weight?.run { compareTo(BigDecimal.ZERO) != 0 } ?: false

private fun SyncVariant?.hasValidWeight() =
    this?.weight?.run { compareTo(BigDecimal("0.5")) == 0 || compareTo(BigDecimal("30.0")) >= 0 } ?: false