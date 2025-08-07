package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.component.accordion.AccordionPanel
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.components.ProgressOverlay
import de.hinundhergestellt.jhuh.components.VaadinCoroutineScope
import de.hinundhergestellt.jhuh.components.accordionSummary
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.grid
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.rangeMultiSelectionMode
import de.hinundhergestellt.jhuh.components.textColumn
import kotlinx.coroutines.CoroutineScope
import org.springframework.stereotype.Component

private class CleanUpDatabasePanel(
    private val service: CleanUpDatabaseService,
    applicationScope: CoroutineScope,
    progressOverlay: ProgressOverlay
) : AccordionPanel() {

    private val cleanUpButton: Button
    private val itemsGrid: Grid<CleanUpItem>

    private val vaadinScope = VaadinCoroutineScope(this, applicationScope, progressOverlay)

    init {
        accordionSummary(
            "Datenbank: Datenbestand aufräumen",
            "Vergleicht den Inhalt der Datenbank mit den Produktkatalogen und bietet Möglichkeiten zum Aufräumen"
        )

        setWidthFull()
        addOpenedChangeListener { if (it.isOpened) refresh() }

        horizontalLayout {
            justifyContentMode = FlexComponent.JustifyContentMode.END
            setWidthFull()

            cleanUpButton = button("Markierte bereinigen") {
                isEnabled = false
                addClickListener { deleteSelectedItems() }
            }
        }
        itemsGrid = grid<CleanUpItem> {
            emptyStateText = "Keine aufzuräumenden Einträge gefunden."
            textColumn("Sachverhalt") { it.message }
            rangeMultiSelectionMode()
            addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
            addSelectionListener { validateActions() }
        }
    }

    private fun validateActions() {
        cleanUpButton.isEnabled = itemsGrid.selectedItems.isNotEmpty()
    }

    private fun refresh() {
        vaadinScope.launchWithReporting {
            application { service.refresh(::report) }
            itemsGrid.setItems(service.items)
        }
    }

    private fun deleteSelectedItems() {
        vaadinScope.launchWithReporting {
            application {
                service.cleanUp(itemsGrid.selectedItems, ::report)
                service.refresh(::report)
            }
            itemsGrid.setItems(service.items)
        }
    }
}

@Component
@VaadinSessionScope
class CleanUpDatabasePanelFactory(
    private val service: CleanUpDatabaseService,
    private val applicationScope: CoroutineScope,
): (ProgressOverlay) -> AccordionPanel {

    override fun invoke(progressOverlay: ProgressOverlay): AccordionPanel =
        CleanUpDatabasePanel(service, applicationScope, progressOverlay)
}