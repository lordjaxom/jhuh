package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMediaClient
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service

@Service
@VaadinSessionScope
class MaintenanceService(
    private val mediaClient: ShopifyMediaClient
) {

    suspend fun findUnusedFiles() = mediaClient.fetchAll("used_in:none").toList()

    suspend fun deleteUnusedFiles(files: List<ShopifyMedia>) {
        mediaClient.delete(files)
    }
}
