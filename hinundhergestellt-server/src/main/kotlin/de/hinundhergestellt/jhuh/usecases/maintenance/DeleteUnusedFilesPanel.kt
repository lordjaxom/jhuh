package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.component.Unit
import com.vaadin.flow.component.accordion.AccordionPanel
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.components.ProgressOverlay
import de.hinundhergestellt.jhuh.components.VaadinCoroutineScope
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
) : AccordionPanel("Ungenutzte Dateien in Shopify löschen") {

    private val deleteButton: Button
    private val filesGrid: Grid<ShopifyMedia>

    private val vaadinScope = VaadinCoroutineScope(this, applicationScope, progressOverlay)

    init {
        setWidthFull()
        addOpenedChangeListener { if (it.isOpened) loadUnusedFiles() }

        horizontalLayout {
            justifyContentMode = FlexComponent.JustifyContentMode.END
            setWidthFull()

            deleteButton = button("Markierte löschen") {
                isEnabled = false
                addClickListener { deleteUnusedFiles() }
            }
        }
        filesGrid = grid<ShopifyMedia> {
            textColumn("Name") { it.fileName }
            rangeMultiSelectionMode()
            setWidthFull()
            setHeight(400.0F, Unit.PIXELS)
            addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
            addSelectionListener { validateUnusedFilesActions() }
        }
    }

    private fun loadUnusedFiles() {
        vaadinScope.launchWithReporting {
            report("Lade ungenutzte Dateien von Shopify...")
            val files = application { service.findAll() }
            filesGrid.setItems(files)
        }
    }

    private fun validateUnusedFilesActions() {
        val selectedItems = filesGrid.selectedItems
        deleteButton.isEnabled = selectedItems.isNotEmpty()
    }

    private fun deleteUnusedFiles() {
        val selectedItems = filesGrid.selectedItems
        if (selectedItems.isEmpty()) return

        vaadinScope.launchWithReporting {
            report("Lösche ${selectedItems.size} ungenutzte Dateien...")
            application { service.delete(selectedItems.toList()) }
            loadUnusedFiles()
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