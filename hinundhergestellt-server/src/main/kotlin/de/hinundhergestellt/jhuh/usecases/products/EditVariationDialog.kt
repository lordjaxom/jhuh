package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.data.binder.ValidationException
import de.hinundhergestellt.jhuh.components.bind
import de.hinundhergestellt.jhuh.components.binder
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.footer
import de.hinundhergestellt.jhuh.components.formLayout
import de.hinundhergestellt.jhuh.components.header
import de.hinundhergestellt.jhuh.components.textField
import de.hinundhergestellt.jhuh.components.toProperty
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private class EditVariationDialog(
    private val variation: ArtooMappedVariation,
    private val callback: (Boolean) -> Unit
) : Dialog() {

    private val binder = binder<ArtooMappedVariation>()

    init {
        width = "500px"
        headerTitle = "Variation bearbeiten"

        header {
            button(VaadinIcon.CLOSE) {
                addThemeVariants(ButtonVariant.LUMO_TERTIARY)
                addClickShortcut(Key.ESCAPE)
                addClickListener { close(); callback(false) }
            }
        }
        formLayout {
            setAutoResponsive(true)
            isExpandColumns = true
            isExpandFields = true

            header("Stammdaten aus ready2order")
            textField("Name") {
                setWidthFull()
                bind(binder)
                    .asRequired("Name darf nicht leer sein.")
                    .toProperty(ArtooMappedVariation::name)
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

        binder.readBean(variation)
    }

    private fun save() {
        try {
            val hasChanges = binder.hasChanges()
            if (hasChanges) binder.writeBean(variation)
            close()
            callback(hasChanges)
        } catch (_: ValidationException) {
        }
    }
}

// TODO move to another file
suspend inline fun <T : Dialog, R> suspendableDialog(crossinline dialogProvider: ((R) -> Unit) -> T) =
    suspendCancellableCoroutine {
        val dialog = dialogProvider { result -> it.resume(result) }
        it.invokeOnCancellation { dialog.close() }
        dialog.open()
    }

suspend fun editVariation(variation: ArtooMappedVariation) = suspendableDialog { EditVariationDialog(variation, it) }