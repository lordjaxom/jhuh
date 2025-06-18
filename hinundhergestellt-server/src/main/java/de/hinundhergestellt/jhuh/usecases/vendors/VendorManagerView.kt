package de.hinundhergestellt.jhuh.usecases.vendors

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.data.provider.AbstractBackEndDataProvider
import com.vaadin.flow.data.provider.Query
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.GridActionButton
import de.hinundhergestellt.jhuh.components.addActionsColumn
import de.hinundhergestellt.jhuh.components.addTextColumn
import de.hinundhergestellt.jhuh.components.vaadinCoroutineScope
import kotlinx.coroutines.launch
import kotlin.streams.asStream

@Route
@PageTitle("Herstellerverwaltung")
class VendorManagerView(
    private val service: VendorManagerService
) : VerticalLayout() {

    private val addButton = Button()
    private val vendorsGrid = Grid<VendorItem>()

    private val coroutineScope = vaadinCoroutineScope(this)

    init {
        setHeightFull()
        width = "1170px"
        style.setMargin("0 auto")

        configureHeader()
        configureVendorsGrid()
    }

    private fun configureHeader() {
        addButton.apply {
            text = "Hinzufügen"
            addClickListener { editVendor(VendorItem()) }
        }

        add(HorizontalLayout(addButton).apply {
            justifyContentMode = JustifyContentMode.END
            setWidthFull()
        })
    }

    private fun configureVendorsGrid() {
        vendorsGrid.addTextColumn("Bezeichnung", flexGrow = 5) { it.name }
        vendorsGrid.addTextColumn("E-Mail", flexGrow = 5) { it.email }
        vendorsGrid.addTextColumn("Adresse", flexGrow = 20) { it.address.replace("\n", ", ") }
        vendorsGrid.addActionsColumn(2) { vendorActions(it) }
        vendorsGrid.dataProvider = VendorsDataProvider()
        vendorsGrid.setSizeFull()
        vendorsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
        add(vendorsGrid)
    }

    private fun editVendor(vendor: VendorItem) = coroutineScope.launch {
        if (editVendorDialog(vendor)) {
            service.saveVendor(vendor)
            if (vendor.id != null) vendorsGrid.dataProvider.refreshItem(vendor)
            else vendorsGrid.dataProvider.refreshAll()
        }
    }

    private fun deleteVendor(vendor: VendorItem) {
        service.deleteVendor(vendor)
        vendorsGrid.dataProvider.refreshAll()
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