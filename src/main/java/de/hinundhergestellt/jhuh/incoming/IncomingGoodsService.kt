package de.hinundhergestellt.jhuh.incoming

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import org.springframework.stereotype.Service

@Service
@VaadinSessionScope
class IncomingGoodsService(
    private val artooDataStore: ArtooDataStore
) {
    val incomings = mutableListOf<Incoming>()

    fun fetch(filter: String?, offset: Int, limit: Int): Sequence<IncomingArticle> {
        if (filter.isNullOrBlank()) {
            return sequenceOf()
        }
        return artooDataStore.findAllProducts()
            .flatMap { product -> product.variations.asSequence().map { IncomingArticle(product, it) } }
            .filter { it.filterBy(filter) }
            .drop(offset)
            .take(limit)
    }

    fun createIncoming(article: IncomingArticle, count: Int) {
        incomings.add(Incoming(article, count))
    }
}

class IncomingArticle(
    val product: ArtooMappedProduct,
    val variation: ArtooMappedVariation
) {
    val fullName = if (product.hasOnlyDefaultVariant) product.name else "${product.name} ${variation.name}"
    val label = "${variation.barcode} - $fullName"

    internal fun filterBy(filter: String) =
        if (filter.toULongOrNull(10) != null) variation.barcode?.startsWith(filter) ?: false
        else filter.split("""\s+""".toRegex()).all { fullName.contains(it, ignoreCase = true) }
}

class Incoming(
    article: IncomingArticle,
    val count: Int
) {
    val label by article::label
}