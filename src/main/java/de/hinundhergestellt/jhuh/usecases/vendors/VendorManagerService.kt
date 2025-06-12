package de.hinundhergestellt.jhuh.usecases.vendors

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendorRepository
import de.hinundhergestellt.jhuh.util.fieldIfNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@VaadinSessionScope
class VendorManagerService(
    private val syncVendorRepository: SyncVendorRepository,
    private val syncProductRepository: SyncProductRepository
) {
    val vendors: List<VendorItem> get() = syncVendorRepository.findAll().map { VendorItem(it) }

    fun canDelete(vendor: VendorItem) = !syncProductRepository.existsByVendor(vendor.toSyncVendor())

    @Transactional
    fun saveVendor(vendor: VendorItem) {
        syncVendorRepository.save(vendor.toSyncVendor())
    }

    @Transactional
    fun deleteVendor(vendor: VendorItem) {
        syncVendorRepository.delete(vendor.toSyncVendor())
    }
}

class VendorItem(
   private val syncVendor: SyncVendor? = null
) {
    val id = syncVendor?.id
    var name by fieldIfNull(syncVendor, SyncVendor::name, "")
    var email by fieldIfNull(syncVendor, SyncVendor::email, "")
    var address by fieldIfNull(syncVendor, SyncVendor::address, "")

    fun toSyncVendor() = syncVendor ?: SyncVendor(name, address, email)
}
