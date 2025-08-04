package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.accordion
import de.hinundhergestellt.jhuh.components.progressOverlay
import kotlinx.coroutines.CoroutineScope

@Route
@PageTitle("Wartungsarbeiten")
class MaintenanceView(
    service: DeleteUnusedFilesService,
    applicationScope: CoroutineScope
) : VerticalLayout() {

    private val progressOverlay = progressOverlay()
    private val deleteUnusedFilesPanel = DeleteUnusedFilesPanel(service, progressOverlay, applicationScope)

    init {
        setHeightFull()
        width = "1170px"
        style.setMargin("0 auto")

        accordion {
            setWidthFull()
            add(deleteUnusedFilesPanel)
        }
    }
}