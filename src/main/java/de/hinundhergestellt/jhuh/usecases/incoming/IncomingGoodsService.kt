package de.hinundhergestellt.jhuh.usecases.incoming

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.components.Article
import org.springframework.stereotype.Service

@Service
@VaadinSessionScope
class IncomingGoodsService {

    val incomings = mutableListOf<Incoming>()

    fun createIncoming(article: Article, count: Int) {
        incomings.add(Incoming(article, count))
    }
}

class Incoming(
    article: Article,
    val count: Int
) {
    val label by article::label
}