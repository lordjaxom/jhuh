package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.component.accordion.AccordionPanel
import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.components.ProgressOverlay
import de.hinundhergestellt.jhuh.components.VaadinContextSwitcher
import de.hinundhergestellt.jhuh.components.vaadinScope
import kotlinx.coroutines.CoroutineScope
import org.springframework.stereotype.Component

private class ReconcileFromShopifyPanel(
    private val service: ReconcileFromShopifyService,
    private val applicationScope: CoroutineScope,
    private val progressOverlay: ProgressOverlay
) : AccordionPanel("Produkte aus Shopify herunterladen") {

    private val vaadinScope = vaadinScope(this)

    init {
        setWidthFull()
        addOpenedChangeListener { if (it.isOpened) refresh() }
    }

    private fun refresh() {
        VaadinContextSwitcher(vaadinScope, applicationScope, progressOverlay).launch {
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