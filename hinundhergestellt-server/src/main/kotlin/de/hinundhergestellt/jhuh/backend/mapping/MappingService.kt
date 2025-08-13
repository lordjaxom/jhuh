package de.hinundhergestellt.jhuh.backend.mapping

import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.streams.asSequence

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
            .map { it.replace(INVALID_TAG_CHARACTERS, "") }
        return (categoryTags + vendorTypeTags).toSet()
    }

    fun checkForProblems(artoo: ArtooMappedProduct, sync: SyncProduct) = buildList {
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

    fun checkForProblems(artoo: ArtooMappedVariation, sync: SyncVariant?) = buildList {
        if (artoo.barcode == null) add(MappingProblem("Variation hat keinen Barcode", true))
        if (artoo.name.isEmpty()) add(MappingProblem("Variation hat keinen Namen", true))
        if (!sync.hasWeight()) add(MappingProblem("Variation hat keine Gewichtsangabe", true))
    }
}

private fun SyncVariant?.hasWeight() = this?.weight?.run { compareTo(BigDecimal.ZERO) != 0 } ?: false