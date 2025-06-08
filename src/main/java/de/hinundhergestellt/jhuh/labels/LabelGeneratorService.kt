package de.hinundhergestellt.jhuh.labels

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.components.Article
import de.hinundhergestellt.jhuh.sync.SyncProduct
import de.hinundhergestellt.jhuh.sync.SyncProductRepository
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.stereotype.Service

@Service
@VaadinSessionScope
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
class LabelGeneratorService(
    private val syncProductRepository: SyncProductRepository
) {
    val labels = mutableListOf<Label>()

    fun createLabel(article: Article, count: Int) {
        val syncProduct = syncProductRepository.findByArtooId(article.product.id)
        labels.add(Label(article, syncProduct, count))
    }
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