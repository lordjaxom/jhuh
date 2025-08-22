package de.hinundhergestellt.jhuh.usecases.products

import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendorRepository
import de.hinundhergestellt.jhuh.vendors.rayher.datastore.RayherDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class EditProductService(
    private val mappingService: MappingService,
    private val shopTexterService: ShopTexterService,
    private val rayherDataStore: RayherDataStore,
    private val syncVendorRepository: SyncVendorRepository
) {
    val vendors get() = syncVendorRepository.findAll()

    fun inheritedTags(syncProduct: SyncProduct, artooProduct: ArtooMappedProduct) =
        mappingService.inheritedTags(syncProduct, artooProduct).toMutableSet()

    fun sanitizeTag(tag: String) = mappingService.sanitizeTag(tag)

    fun generateProductDetails(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct) =
        shopTexterService.generateProductDetails(artooProduct, syncProduct)

    fun generateProductTags(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct) =
        shopTexterService.generateProductTags(artooProduct, syncProduct)

    fun canFillInValues(artooProduct: ArtooMappedProduct) = findRayherVariations(artooProduct) != null

    fun fillInValues(artooProduct: ArtooMappedProduct): FilledInProductValues {
        return findRayherVariations(artooProduct)
            ?.let { rayherVariations ->
                FilledInProductValues(
                    vendor = syncVendorRepository.findByNameIgnoreCase("Rayher")!!,
                    description = rayherVariations.map { it.description }.distinct().takeIf { it.size == 1 }?.getOrNull(0),
                    weight = rayherVariations.map { it.weight }.distinct().takeIf { it.size == 1 }?.getOrNull(0)
                )
            }
            ?: FilledInProductValues()
    }

    private fun findRayherVariations(artooProduct: ArtooMappedProduct) =
        artooProduct.variations
            .filter { it.barcode != null }
            .mapNotNull { rayherDataStore.findByEan(it.barcode!!) }
            .takeIf { it.size == artooProduct.variations.size }
}

class FilledInProductValues(
    val vendor: SyncVendor? = null,
    val description: String? = null,
    val weight: BigDecimal? = null
)