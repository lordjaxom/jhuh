package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.UI
import com.vaadin.flow.server.ErrorEvent
import com.vaadin.flow.server.VaadinSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

private class VaadinDispatcher(
    private val ui: UI = UI.getCurrent(),
) : CoroutineDispatcher(), CoroutineExceptionHandler {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        ui.access { block.run() }
    }

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        ui.access {
            VaadinSession.getCurrent().errorHandler
                ?.error(ErrorEvent(exception))
                ?: throw exception
        }
    }
}

fun vaadinScope(component: Component): CoroutineScope {
    val job = SupervisorJob()
    component.addDetachListener { job.cancel() }
    return CoroutineScope(job + VaadinDispatcher())
}