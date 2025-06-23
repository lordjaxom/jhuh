package de.hinundhergestellt.jhuh.usecases.labels

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.data.binder.ValidationException
import de.hinundhergestellt.jhuh.components.beanValidationBinder
import de.hinundhergestellt.jhuh.components.bind
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.footer
import de.hinundhergestellt.jhuh.components.header
import de.hinundhergestellt.jhuh.components.integerField
import de.hinundhergestellt.jhuh.components.toProperty
import de.hinundhergestellt.jhuh.components.verticalLayout
import jakarta.validation.constraints.Min
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private class EditCountDialog(
    count: Int,
    private val callback: (Int?) -> Unit
) : Dialog() {

    private val binder = beanValidationBinder<EditCountBean>()
    private val bean = EditCountBean(count)

    init {
        width = "500px"
        headerTitle = "Anzahl bearbeiten"

        header {
            button(VaadinIcon.CLOSE) {
                addThemeVariants(ButtonVariant.LUMO_TERTIARY)
                addClickListener { close(); callback(null) }
            }
        }
        verticalLayout {
            isSpacing = false
            isPadding = false

            integerField("Anzahl") {
                setWidthFull()
                bind(binder).toProperty(EditCountBean::count)
                focus()
            }
        }
        footer {
            button("Speichern") {
                addThemeVariants(ButtonVariant.LUMO_PRIMARY)
                addClickShortcut(Key.ENTER)
                addClickListener { save() }
            }
        }

        binder.readBean(bean)
    }

    private fun save() {
        try {
            binder.writeBean(bean)
            close()
            callback(bean.count)
        } catch (_: ValidationException) {
        }
    }
}

class EditCountBean(
    @get:Min(value = 1, message = "Anzahl muss größer als 1 sein")
    var count: Int
)

suspend fun editCountDialog(count: Int) =
    suspendCancellableCoroutine {
        val dialog = EditCountDialog(count) { result -> it.resume(result)}
        it.invokeOnCancellation { dialog.close() }
        dialog.open()
    }