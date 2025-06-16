package de.hinundhergestellt.jhuh.usecases.labels

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.components.Article
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
        labels.add(ArticleLabel(article, syncProduct, count))
    }
}

sealed interface Label {
    val vendor: String
    val name: String
    val variant: String
    val barcode: String?
    val price: String
    val count: Int
}

class EmptyLabel(
    override val count: Int
): Label {
    override val vendor = ""
    override val name = ""
    override val variant = ""
    override val barcode = ""
    override val price = ""
}

class ArticleLabel(
    article: Article,
    syncProduct: SyncProduct?,
    override val count: Int
) : Label {
    override val vendor = syncProduct?.vendor?.name ?: ""
    override val name by article.product::name
    override val variant = sequenceOf(article.variation.itemNumber, article.variation.name).filterNotNull().joinToString(" ")
    override val barcode by article.variation::barcode
    override val price: String = "${article.variation.price.toPlainString()} €"
}