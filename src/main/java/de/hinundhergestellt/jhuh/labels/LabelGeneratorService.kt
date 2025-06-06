package de.hinundhergestellt.jhuh.labels

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.service.ready2order.ArtooDataStore
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedProduct
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedVariation
import de.hinundhergestellt.jhuh.sync.SyncProduct
import de.hinundhergestellt.jhuh.sync.SyncProductRepository
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.stereotype.Service

@Service
@VaadinSessionScope
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
class LabelGeneratorService(
    private val artooDataStore: ArtooDataStore,
    private val syncProductRepository: SyncProductRepository
) {
    val articles
        get() = artooDataStore.findAllProducts().flatMap { product -> product.variations.asSequence().map { Article(product, it) } }

    val labels = mutableListOf<Label>()

    fun createLabel(article: Article, count: Int) {
        val syncProduct = syncProductRepository.findByArtooId(article.product.id)
        labels.add(Label(article, syncProduct, count))
    }
}

class Article(
    val product: ArtooMappedProduct,
    val variation: ArtooMappedVariation
) {
    val fullName = if (product.hasOnlyDefaultVariant) product.name else "${product.name} ${variation.name}"
    val label = "${variation.barcode} - $fullName"

    internal fun filterBy(filter: String) =
        if (filter.toULongOrNull(10) != null) variation.barcode?.startsWith(filter) ?: false
        else filter.split("""\s+""".toRegex()).all { fullName.contains(it, ignoreCase = true) }
}

class Label(
    article: Article,
    syncProduct: SyncProduct?,
    val count: Int
) {
    val vendor = syncProduct?.vendor ?: ""
    val name by article.product::name
    val variant = sequenceOf(article.variation.itemNumber, article.variation.name).filterNotNull().joinToString(" ")
    val barcode by article.variation::barcode
}