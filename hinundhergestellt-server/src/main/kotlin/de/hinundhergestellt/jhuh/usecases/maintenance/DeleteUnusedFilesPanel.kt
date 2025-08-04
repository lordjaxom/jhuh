package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.component.Unit
import com.vaadin.flow.component.accordion.AccordionPanel
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import de.hinundhergestellt.jhuh.components.ProgressOverlay
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.grid
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.launchWithProgress
import de.hinundhergestellt.jhuh.components.rangeMultiSelectionMode
import de.hinundhergestellt.jhuh.components.textColumn
import de.hinundhergestellt.jhuh.components.vaadinScope
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

class DeleteUnusedFilesPanel(
    private val service: DeleteUnusedFilesService,
    private val progressOverlay: ProgressOverlay,
    private val applicationScope: CoroutineScope
) : AccordionPanel("Ungenutzte Dateien in Shopify löschen") {

    private val deleteButton: Button
    private val filesGrid: Grid<ShopifyMedia>

    private val vaadinScope = vaadinScope(this)

    init {
        isOpened = false

        setWidthFull()
        addOpenedChangeListener { toggleUnusedImages(it.isOpened) }

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

    private fun toggleUnusedImages(opened: Boolean) {
        if (!opened) {
            filesGrid.setItems(listOf())
            return
        }

        vaadinScope.launchWithProgress(progressOverlay) {
            loadUnusedFiles()
        }
    }

    private fun validateUnusedFilesActions() {
        val selectedItems = filesGrid.selectedItems
        deleteButton.isEnabled = selectedItems.isNotEmpty()
    }

    private fun deleteUnusedFiles() {
        val selectedItems = filesGrid.selectedItems
        if (selectedItems.isEmpty()) return

        vaadinScope.launchWithProgress(progressOverlay) {
            progressOverlay.text = "Lösche ${selectedItems.size} ungenutzte Dateien..."
            withContext(applicationScope.coroutineContext) { service.delete(selectedItems.toList()) }

            loadUnusedFiles()
        }
    }

    private suspend fun loadUnusedFiles() {
        progressOverlay.text = "Lade ungenutzte Dateien von Shopify..."
        val files = withContext(applicationScope.coroutineContext) { service.findAll() }
        filesGrid.setItems(files)
    }
}