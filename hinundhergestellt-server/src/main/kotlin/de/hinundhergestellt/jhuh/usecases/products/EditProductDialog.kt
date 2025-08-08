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
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private class EditProductDialog(
    private val product: ArtooMappedProduct,
    private val callback: (Boolean) -> Unit
) : Dialog() {

    private val binder = binder<ArtooMappedProduct>()

    init {
        width = "500px"
        headerTitle = "Produkt bearbeiten"

        header {
            button(VaadinIcon.CLOSE) {
                addThemeVariants(ButtonVariant.LUMO_TERTIARY)
                addClickShortcut(Key.ESCAPE)
                addClickListener { close(); callback(false) }
            }
        }
        formLayout {
            textField("Name") {
                setWidthFull()
                bind(binder)
                    .asRequired("Name darf nicht leer sein.")
                    .toProperty(ArtooMappedProduct::name)
                focus()
            }
            textField("Beschreibung") {
                setWidthFull()
                bind(binder)
                    .asRequired("Beschreibung darf nicht leer sein.")
                    .toProperty(ArtooMappedProduct::description)
            }
        }
        footer {
            button("Speichern") {
                addThemeVariants(ButtonVariant.LUMO_PRIMARY)
                addClickShortcut(Key.ENTER)
                addClickListener { save() }
            }
        }

        binder.readBean(product)
    }

    private fun save() {
        try {
            binder.writeBean(product)
            close()
            callback(true)
        } catch (_: ValidationException) {
        }
    }
}

suspend fun editProduct(product: ArtooMappedProduct) = dialog { EditProductDialog(product, it) }

suspend fun renameProductDialog(product: ArtooMappedProduct) =
    suspendCancellableCoroutine {
        val dialog = EditProductDialog(product) { result -> it.resume(result) }
        it.invokeOnCancellation { dialog.close() }
        dialog.open()
    }