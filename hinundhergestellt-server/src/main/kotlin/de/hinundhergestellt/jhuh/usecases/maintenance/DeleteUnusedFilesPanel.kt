package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.component.Unit
import com.vaadin.flow.component.accordion.AccordionPanel
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.dom.Style
import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.components.ProgressOverlay
import de.hinundhergestellt.jhuh.components.VaadinCoroutineScope
import de.hinundhergestellt.jhuh.components.accordionSummary
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.grid
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.rangeMultiSelectionMode
import de.hinundhergestellt.jhuh.components.textColumn
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import kotlinx.coroutines.CoroutineScope
import org.springframework.stereotype.Component

private class DeleteUnusedFilesPanel(
    private val service: DeleteUnusedFilesService,
    applicationScope: CoroutineScope,
    progressOverlay: ProgressOverlay
) : AccordionPanel() {

    private val deleteButton: Button
    private val filesGrid: Grid<ShopifyMedia>

    private val vaadinScope = VaadinCoroutineScope(this, applicationScope, progressOverlay)

    init {
        accordionSummary(
            "Shopify: Ungenutzte Dateien löschen",
            "Lädt eine Liste von laut Shopify ungenutzten Dateien und ermöglicht es, diese zu löschen"
        )

        setWidthFull()
        addOpenedChangeListener { if (it.isOpened) refresh() }

        horizontalLayout {
            setWidthFull()
            justifyContentMode = FlexComponent.JustifyContentMode.END
            style.setFlexWrap(Style.FlexWrap.WRAP)

            deleteButton = button("Markierte löschen") {
                isEnabled = false
                addThemeVariants(ButtonVariant.LUMO_ERROR)
                style.setMarginInlineEnd("auto")
                addClickListener { deleteSelectedFiles() }
            }
            button("Markierte ignorieren") {
                addClickListener { ignoreSelectedFiles() }
            }
            button("Aktualisieren") {
                addClickListener { refresh() }
            }
        }
        filesGrid = grid<ShopifyMedia> {
            emptyStateText = "Keine ungenutzten Dateien gefunden."
            textColumn("Name") { it.fileName }
            rangeMultiSelectionMode()
            setWidthFull()
            setHeight(400.0F, Unit.PIXELS)
            addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
            addSelectionListener { validateActions() }
        }
    }

    private fun validateActions() {
        deleteButton.isEnabled = filesGrid.selectedItems.isNotEmpty()
    }

    private fun refresh() {
        vaadinScope.launchWithReporting {
            application { service.refresh(::report) }
            filesGrid.setItems(service.files)
            filesGrid.recalculateColumnWidths()
        }
    }

    private fun ignoreSelectedFiles() {
        service.ignore(filesGrid.selectedItems)
        filesGrid.setItems(service.files)
    }

    private fun deleteSelectedFiles() {
        vaadinScope.launchWithReporting {
            application {
                service.delete(filesGrid.selectedItems, ::report)
                service.refresh(::report)
            }
            filesGrid.setItems(service.files)
        }
    }
}

@Component
@VaadinSessionScope
class DeleteUnusedFilesPanelFactory(
    private val service: DeleteUnusedFilesService,
    private val applicationScope: CoroutineScope,
): (ProgressOverlay) -> AccordionPanel {

    override fun invoke(progressOverlay: ProgressOverlay): AccordionPanel =
        DeleteUnusedFilesPanel(service, applicationScope, progressOverlay)
}