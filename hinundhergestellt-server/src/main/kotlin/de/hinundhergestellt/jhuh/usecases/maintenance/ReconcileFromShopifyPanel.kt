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
import de.hinundhergestellt.jhuh.components.componentColumn
import de.hinundhergestellt.jhuh.components.ellipsisColumn
import de.hinundhergestellt.jhuh.components.grid
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.rangeMultiSelectionMode
import de.hinundhergestellt.jhuh.components.textColumn
import de.hinundhergestellt.jhuh.usecases.maintenance.ReconcileFromShopifyService.Item
import de.hinundhergestellt.jhuh.usecases.maintenance.ReconcileFromShopifyService.ProductItem
import de.hinundhergestellt.jhuh.usecases.maintenance.ReconcileFromShopifyService.VariantItem
import kotlinx.coroutines.CoroutineScope
import org.springframework.stereotype.Component

private class ReconcileFromShopifyPanel(
    private val service: ReconcileFromShopifyService,
    applicationScope: CoroutineScope,
    progressOverlay: ProgressOverlay
) : AccordionPanel() {

    private val applyButton: Button
    private val itemsGrid: Grid<Item>

    private val vaadinScope = VaadinCoroutineScope(this, applicationScope, progressOverlay)

    init {
        accordionSummary(
            "Shopify: Produktinformationen herunterladen",
            "Gleicht den lokalen Datenbestand für in ready2order vorhandene Produkte mit nur in Shopify getätigten Änderungen ab"
        )
        setWidthFull()

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
        itemsGrid = grid<Item> {
            emptyStateText = "Keine abweichenden Einträge gefunden."
            componentColumn({ gridIconColumn(it)}) {
                isAutoWidth = true
                flexGrow = 0
            }
            textColumn({ it.title }) {
                setHeader("Bezeichnung")
                flexGrow = 1
            }
            ellipsisColumn({ it.message }) {
                setHeader("Sachverhalt")
                flexGrow = 2
            }
            rangeMultiSelectionMode()
            setWidthFull()
            setHeight(600.0F, Unit.PIXELS)
            addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
            addSelectionListener { validateActions() }

            setItems(service.items)
        }
    }

    private fun refresh() {
        vaadinScope.launchWithReporting {
            application { service.refresh(::report) }
            itemsGrid.setItems(service.items)
            itemsGrid.recalculateColumnWidths()
        }
    }

    private fun applySelectedItems() {
        vaadinScope.launchWithReporting {
            application {
                service.apply(itemsGrid.selectedItems, ::report)
                service.rebuild(::report)
            }
            itemsGrid.setItems(service.items)
            itemsGrid.recalculateColumnWidths()
        }
    }

    private fun validateActions() {
        applyButton.isEnabled = itemsGrid.selectedItems.isNotEmpty()
    }

    private fun gridIconColumn(item: Item) =
        when (item) {
            is ProductItem -> CustomIcon.PRODUCT
            is VariantItem -> CustomIcon.VARIATION
        }.create().apply { color = "var(--lumo-secondary-text-color)" }
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