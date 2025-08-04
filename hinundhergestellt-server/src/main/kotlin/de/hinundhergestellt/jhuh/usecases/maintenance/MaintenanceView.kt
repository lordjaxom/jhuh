package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.component.Unit
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.ProgressOverlay
import de.hinundhergestellt.jhuh.components.accordion
import de.hinundhergestellt.jhuh.components.accordionPanel
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.grid
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.progressOverlay
import de.hinundhergestellt.jhuh.components.rangeMultiSelectionMode
import de.hinundhergestellt.jhuh.components.showErrorNotification
import de.hinundhergestellt.jhuh.components.textColumn
import de.hinundhergestellt.jhuh.components.vaadinScope
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Route
@PageTitle("Wartungsarbeiten")
class MaintenanceView(
    private val service: MaintenanceService,
    private val applicationScope: CoroutineScope
) : VerticalLayout() {

    private val deleteUnusedFilesButton: Button
    private val unusedFilesGrid: Grid<ShopifyMedia>
    private val progressOverlay: ProgressOverlay

    private val vaadinScope = vaadinScope(this)

    init {
        setHeightFull()
        width = "1170px"
        style.setMargin("0 auto")

        accordion {
            setWidthFull()

            accordionPanel("Ungenutzte Dateien in Shopify löschen") {
                setWidthFull()
                addOpenedChangeListener { toggleUnusedImages(it.isOpened) }

                horizontalLayout {
                    justifyContentMode = FlexComponent.JustifyContentMode.END
                    setWidthFull()

                    deleteUnusedFilesButton = button("Markierte löschen") {
                        isEnabled = false
                        addClickListener { deleteUnusedFiles() }
                    }
                }
                unusedFilesGrid = grid<ShopifyMedia> {
                    textColumn("Name") { it.fileName }
                    rangeMultiSelectionMode()
                    setWidthFull()
                    setHeight(400.0F, Unit.PIXELS)
                    addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
                    addSelectionListener { validateUnusedFilesActions() }
                }
            }
        }
        progressOverlay = progressOverlay()
    }

    private fun toggleUnusedImages(opened: Boolean) {
        if (!opened) {
            unusedFilesGrid.setItems(listOf())
            return
        }

        launchWithProgress {
            progressOverlay.text = "Lade ungenutzte Dateien von Shopify..."
            unusedFilesGrid.setItems(withContext(applicationScope.coroutineContext) { service.findUnusedFiles() })
        }
    }

    private fun validateUnusedFilesActions() {
        val selectedItems = unusedFilesGrid.selectedItems
        deleteUnusedFilesButton.isEnabled = selectedItems.isNotEmpty()
    }

    private fun deleteUnusedFiles() {
        val selectedItems = unusedFilesGrid.selectedItems
        if (selectedItems.isEmpty()) return

        launchWithProgress {
            progressOverlay.text = "Lösche ${selectedItems.size} ungenutzte Dateien..."
            withContext(applicationScope.coroutineContext) { service.deleteUnusedFiles(selectedItems.toList()) }

            progressOverlay.text = "Lade ungenutzte Dateien von Shopify..."
            unusedFilesGrid.setItems(withContext(applicationScope.coroutineContext) { service.findUnusedFiles() })
        }
    }

    private fun launchWithProgress(block: suspend CoroutineScope.() -> kotlin.Unit) = vaadinScope.launch {
        progressOverlay.isVisible = true
        try {
            block()
        } catch (e: Throwable) {
            showErrorNotification(e)
        } finally {
            progressOverlay.isVisible = false
        }
    }
}