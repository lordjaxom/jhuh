package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMediaClient
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service

@Service
@VaadinSessionScope
class DeleteUnusedFilesService(
    private val mediaClient: ShopifyMediaClient
) {
    val files = mutableListOf<ShopifyMedia>()

    suspend fun refresh(report: suspend (String) -> Unit){
        report("Lade ungenutzte Dateien aus Shopify...")
        files.clear()
        mediaClient.fetchAll("used_in:none").collect { files.add(it) }
    }

    suspend fun delete(files: Set<ShopifyMedia>, report: suspend (String) -> Unit) {
        report("Lösche ${files.size} ungenutzte Dateien...")
        mediaClient.delete(files.toList())
    }
}
