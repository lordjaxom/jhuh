package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.DetachNotifier
import com.vaadin.flow.component.UI
import com.vaadin.flow.server.ErrorEvent
import com.vaadin.flow.server.ErrorHandler
import com.vaadin.flow.server.VaadinSession
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

fun checkUIThread() {
    require(UI.getCurrent() != null) { "Not running in Vaadin UI thread" }
}

/**
 * Implements [CoroutineDispatcher] on top of Vaadin [UI] and makes sure that all coroutine code runs in the UI thread.
 * Actions done in the UI thread are then automatically pushed by Vaadin Push to the browser.
 */
private data class VaadinDispatcher(val ui: UI) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        ui.access { block.run() }
    }
}

/**
 * If the coroutine fails, redirect the exception to the Vaadin Error Handler
 * (the [VaadinSession.errorHandler] if set; if not,
 * the handler will simply rethrow the exception).
 */
private data class VaadinExceptionHandler(val ui: UI) : CoroutineExceptionHandler {
    override val key: CoroutineContext.Key<*>
        get() = CoroutineExceptionHandler

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        // send the exception to Vaadin
        ui.access {
            val errorHandler: ErrorHandler? = VaadinSession.getCurrent().errorHandler
            if (errorHandler != null) {
                errorHandler.error(ErrorEvent(exception))
            } else {
                throw exception
            }
        }
    }
}

/**
 * Provides the Vaadin Coroutine context for given [ui] (or the current one if none specified).
 */
fun vaadin(ui: UI = UI.getCurrent()): CoroutineContext = VaadinDispatcher(ui) + VaadinExceptionHandler(ui)

fun vaadinContext(component: Component): CoroutineContext {
    val uiCoroutineContext = SupervisorJob()
    val uiCoroutineScope = vaadin()
    component.addDetachListener { uiCoroutineContext.cancel() }
    return uiCoroutineContext + uiCoroutineScope
}
