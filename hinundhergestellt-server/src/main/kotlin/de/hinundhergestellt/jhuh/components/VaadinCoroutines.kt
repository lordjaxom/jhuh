package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasText
import com.vaadin.flow.component.UI
import com.vaadin.flow.server.ErrorEvent
import com.vaadin.flow.server.VaadinSession
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger {  }

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

class VaadinCoroutineScope<T> (
    component: Component,
    private val applicationScope: CoroutineScope,
    private val progress: T?
) : CoroutineScope by vaadinScope(component)
        where T: Component, T: HasText {

    fun launch(block: suspend VaadinCoroutineScope<T>.() -> Unit) = (this as CoroutineScope).launch { block() }

    fun launchWithReporting(block: suspend VaadinCoroutineScope<T>.() -> Unit) = launch { withReporting { block() } }

    suspend fun <R> application(block: suspend CoroutineScope.() -> R) = withContext(applicationScope.coroutineContext) { block() }
    suspend fun <R> vaadin(block: suspend CoroutineScope.() -> R) = withContext(coroutineContext) { block() }

    suspend fun withReporting(block: suspend VaadinCoroutineScope<T>.() -> Unit) {
        progress?.isVisible = true
        try {
            block()
        } catch (e: Throwable) {
            logger.error(e) { "Error in reported coroutine"}
            showErrorNotification(e)
        } finally {
            progress?.isVisible = false
        }
    }

    suspend fun report(message: String) = vaadin { progress?.text = message }
}