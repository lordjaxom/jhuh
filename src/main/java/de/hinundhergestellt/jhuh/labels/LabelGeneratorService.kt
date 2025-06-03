package de.hinundhergestellt.jhuh.labels

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.service.ready2order.ArtooDataStore
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedProduct
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedVariation
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.stereotype.Service

@Service
@VaadinSessionScope
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
class LabelGeneratorService(
    private val artooDataStore: ArtooDataStore
) {
    val articles
        get() = artooDataStore.findAllProducts().flatMap { product -> product.variations.asSequence().map { Article(product, it) } }

    val labels = mutableListOf<Label>()
}

class Article(
    product: ArtooMappedProduct,
    private val variation: ArtooMappedVariation
) {
    val name = if (product.hasOnlyDefaultVariant) product.name else "${product.name} ${variation.name}"
    val label = "${variation.barcode} - $name"
    val barcode by variation::barcode
}

class Label(
    private val article: Article,
    val count: Int
) {
    val barcode by article::barcode
    val name by article::name
}

internal fun Article.filterBy(filter: String) =
    if (filter.toUIntOrNull(10) != null) barcode?.startsWith(filter) ?: false
    else filter.split("""\s+""".toRegex()).all { name.contains(it, ignoreCase = true) }
