package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import org.springframework.stereotype.Service

@Service
@VaadinSessionScope
class ArticleComboBoxService(
    private val artooDataStore: ArtooDataStore
) {
    fun fetch(filter: String?): Sequence<Article> {
        if (filter.isNullOrBlank()) {
            return sequenceOf()
        }
        return artooDataStore.findAllProducts()
            .flatMap { product -> product.variations.asSequence().map { Article(product, it) } }
            .filter { it.filterBy(filter) }
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
