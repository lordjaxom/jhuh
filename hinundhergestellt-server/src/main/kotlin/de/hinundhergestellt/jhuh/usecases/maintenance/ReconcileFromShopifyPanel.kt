package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.component.accordion.AccordionPanel
import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.components.ProgressOverlay
import de.hinundhergestellt.jhuh.components.VaadinCoroutineScope
import kotlinx.coroutines.CoroutineScope
import org.springframework.stereotype.Component

private class ReconcileFromShopifyPanel(
    private val service: ReconcileFromShopifyService,
    applicationScope: CoroutineScope,
    progressOverlay: ProgressOverlay
) : AccordionPanel("Produkte aus Shopify herunterladen") {

    private val vaadinScope = VaadinCoroutineScope(this, applicationScope, progressOverlay)

    init {
        setWidthFull()
        addOpenedChangeListener { if (it.isOpened) refresh() }
    }

    private fun refresh() {
        vaadinScope.launchWithReporting {
            report("Synchronisiere...")
            application { service.synchronize(::report) }
        }
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