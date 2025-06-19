package de.hinundhergestellt.jhuh.usecases.vendors

import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.data.provider.AbstractBackEndDataProvider
import com.vaadin.flow.data.provider.Query
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.GridActionButton
import de.hinundhergestellt.jhuh.components.actionsColumn
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.grid
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.textColumn
import de.hinundhergestellt.jhuh.components.vaadinScope
import kotlinx.coroutines.launch
import kotlin.streams.asStream

@Route
@PageTitle("Herstellerverwaltung")
class VendorManagerView(
    private val service: VendorManagerService
) : VerticalLayout() {

    private val vendorsDataProvider = VendorsDataProvider()

    private val vaadinScope = vaadinScope(this)

    init {
        setHeightFull()
        width = "1170px"
        style.setMargin("0 auto")

        horizontalLayout {
            justifyContentMode = JustifyContentMode.END
            setWidthFull()

            button("Hinzufügen") {
                addClickListener { editVendor(VendorItem()) }
            }
        }
        grid<VendorItem> {
            textColumn("Bezeichnung", flexGrow = 5) { it.name }
            textColumn("E-Mail", flexGrow = 5) { it.email }
            textColumn("Adresse", flexGrow = 20) { it.address.replace("\n", ", ") }
            actionsColumn(2) { vendorActions(it) }
            dataProvider = vendorsDataProvider
            setSizeFull()
            addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
        }
    }

    private fun editVendor(vendor: VendorItem) = vaadinScope.launch {
        if (editVendorDialog(vendor)) {
            service.saveVendor(vendor)
            if (vendor.id != null) vendorsDataProvider.refreshItem(vendor)
            else vendorsDataProvider.refreshAll()
        }
    }

    private fun deleteVendor(vendor: VendorItem) {
        service.deleteVendor(vendor)
        vendorsDataProvider.refreshAll()
    }

    private fun vendorActions(vendor: VendorItem) = listOf(
        GridActionButton(VaadinIcon.EDIT) { editVendor(vendor) },
        GridActionButton(VaadinIcon.TRASH) { deleteVendor(vendor) }.apply { isEnabled = service.canDelete(vendor) }
    )

    private inner class VendorsDataProvider : AbstractBackEndDataProvider<VendorItem, Void?>() {

        override fun fetchFromBackEnd(query: Query<VendorItem, Void?>) =
            service.vendors.asSequence().drop(query.offset).take(query.limit).asStream()

        override fun sizeInBackEnd(query: Query<VendorItem, Void?>) = fetchFromBackEnd(query).count().toInt()
        override fun getId(item: VendorItem) = item.id
    }
}