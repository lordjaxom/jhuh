package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasText
import com.vaadin.flow.component.UI
import com.vaadin.flow.server.ErrorEvent
import com.vaadin.flow.server.VaadinSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

fun CoroutineScope.launchWithProgress(progress: Component, block: suspend CoroutineScope.() -> Unit) =
    launch {
        progress.isVisible = true
        try {
            block()
        } catch (e: Throwable) {
            showErrorNotification(e)
        } finally {
            progress.isVisible = false
        }
    }

class VaadinContextSwitcher<T>(
    private val vaadinScope: CoroutineScope,
    private val applicationScope: CoroutineScope,
    private val progress: T
) where T : Component, T : HasText {

    fun launch(block: suspend VaadinContextSwitcher<T>.() -> Unit) = vaadinScope.launch {
        progress.isVisible = true
        try {
            block()
        } catch (e: Throwable) {
            showErrorNotification(e)
        } finally {
            progress.isVisible = false
        }
    }

    suspend fun application(block: suspend CoroutineScope.() -> Unit) = withContext(applicationScope.coroutineContext) { block() }
    suspend fun vaadin(block: suspend CoroutineScope.() -> Unit) = withContext(vaadinScope.coroutineContext) { block() }

    suspend fun report(message: String) = vaadin { progress.text = message }
}
