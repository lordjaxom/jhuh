package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.accordion
import de.hinundhergestellt.jhuh.components.progressOverlay

@Route
@PageTitle("Wartungsarbeiten")
class MaintenanceView(
    reconcileFromShopifyPanelFactory: ReconcileFromShopifyPanelFactory,
    deleteUnusedFilesPanelFactory: DeleteUnusedFilesPanelFactory,
) : VerticalLayout() {

    private val progressOverlay = progressOverlay()

    init {
        setHeightFull()
        width = "1170px"
        style.setMargin("0 auto")

        accordion {
            setWidthFull()
            add(reconcileFromShopifyPanelFactory(progressOverlay))
            add(deleteUnusedFilesPanelFactory(progressOverlay))
            close()
        }
    }
}