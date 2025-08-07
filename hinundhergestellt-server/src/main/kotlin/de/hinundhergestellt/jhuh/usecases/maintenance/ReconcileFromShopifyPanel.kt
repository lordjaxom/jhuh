package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.component.Unit
import com.vaadin.flow.component.accordion.AccordionPanel
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.components.CustomIcon
import de.hinundhergestellt.jhuh.components.ProgressOverlay
import de.hinundhergestellt.jhuh.components.VaadinCoroutineScope
import de.hinundhergestellt.jhuh.components.accordionSummary
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.grid
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.iconColumn
import de.hinundhergestellt.jhuh.components.rangeMultiSelectionMode
import de.hinundhergestellt.jhuh.components.textColumn
import kotlinx.coroutines.CoroutineScope
import org.springframework.stereotype.Component

private class ReconcileFromShopifyPanel(
    private val service: ReconcileFromShopifyService,
    applicationScope: CoroutineScope,
    progressOverlay: ProgressOverlay
) : AccordionPanel() {

    private val applyButton: Button
    private val itemsGrid: Grid<ReconcileItem>

    private val vaadinScope = VaadinCoroutineScope(this, applicationScope, progressOverlay)

    private var loaded = false

    init {
        accordionSummary(
            "Shopify: Produktinformationen herunterladen",
            "Gleicht den lokalen Datenbestand für in ready2order vorhandene Produkte mit nur in Shopify getätigten Änderungen ab"
        )
        setWidthFull()
        addOpenedChangeListener {
            if (it.isOpened && !loaded) {
                refresh(); loaded = true
            }
        }

        horizontalLayout {
            justifyContentMode = FlexComponent.JustifyContentMode.END
            setWidthFull()

            button("Aktualisieren") {
                addClickListener { refresh() }
            }
            applyButton = button("Markierte übernehmen") {
                isEnabled = false
                addClickListener { applySelectedItems() }
            }
        }
        itemsGrid = grid<ReconcileItem> {
            emptyStateText = "Keine abweichenden Einträge gefunden."
            iconColumn { itemIcon(it).create().apply { color = "var(--lumo-secondary-text-color)" } }
            textColumn("Bezeichnung") { it.title }
            textColumn("Sachverhalte") { it.message }
            rangeMultiSelectionMode()
            setWidthFull()
            setHeight(600.0F, Unit.PIXELS)
            addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
            addSelectionListener { validateActions() }
        }
    }

    private fun refresh() {
        vaadinScope.launchWithReporting {
            application { service.refresh(::report) }
            itemsGrid.setItems(service.items)
        }
    }

    private fun applySelectedItems() {
        vaadinScope.launchWithReporting {
            application {
                service.apply(itemsGrid.selectedItems, ::report)
                service.refresh(::report)
            }
            itemsGrid.setItems(service.items)
        }
    }

    private fun validateActions() {
        applyButton.isEnabled = itemsGrid.selectedItems.isNotEmpty()
    }

    private fun itemIcon(item: ReconcileItem) =
        when (item) {
            is ProductReconcileItem -> CustomIcon.PRODUCT
            is VariantReconcileItem -> CustomIcon.VARIATION
        }
}

@Component
@VaadinSessionScope
class ReconcileFromShopifyPanelFactory(
    private val service: ReconcileFromShopifyService,
    private val applicationScope: CoroutineScope
) : (ProgressOverlay) -> AccordionPanel {

    override fun invoke(progressOverlay: ProgressOverlay): AccordionPanel =
        ReconcileFromShopifyPanel(service, applicationScope, progressOverlay)
}