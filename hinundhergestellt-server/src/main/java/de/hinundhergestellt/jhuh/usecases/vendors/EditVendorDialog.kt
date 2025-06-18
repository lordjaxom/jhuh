package de.hinundhergestellt.jhuh.usecases.vendors

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.data.binder.ValidationException
import com.vaadin.flow.data.value.ValueChangeMode
import de.hinundhergestellt.jhuh.components.beanValidationBinder
import de.hinundhergestellt.jhuh.components.bind
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.footer
import de.hinundhergestellt.jhuh.components.header
import de.hinundhergestellt.jhuh.components.textArea
import de.hinundhergestellt.jhuh.components.textField
import de.hinundhergestellt.jhuh.components.verticalLayout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private class EditVendorDialog(
    private val vendor: VendorItem,
    private val callback: (Boolean) -> Unit
) : Dialog() {

    private val binder = beanValidationBinder<VendorItem>()

    init {
        width = "500px"
        headerTitle = "Hersteller bearbeiten"

        header {
            button(VaadinIcon.CLOSE) {
                addThemeVariants(ButtonVariant.LUMO_TERTIARY)
                addClickListener { close(); callback(false) }
            }
        }
        verticalLayout {
            isSpacing = false
            isPadding = false

            textField("Bezeichnung") {
                isRequired = true
                setWidthFull()
                binder.forField(this).bind(VendorItem::name)
                focus()
            }
            textField("E-Mail") {
                isRequired = true
                setWidthFull()
                binder.forField(this).bind(VendorItem::email)
            }
            textArea("Adresse") {
                isRequired = true
                maxLength = 255
                minRows = 5
                maxRows = 5
                valueChangeMode = ValueChangeMode.EAGER
                setWidthFull()
                addValueChangeListener { helperText = "${value.length}/${maxLength}" }
                binder.forField(this).bind(VendorItem::address)

                // @formatter:off
                element.executeJs("""
                    const input = this.inputElement
                    input.addEventListener('keydown', function(e) {
                        if (e.key === 'Enter') {
                            e.stopPropagation()
                        }            
                    });
                """.trimIndent())
                // @formatter:on
            }
        }
        footer {
            button("Speichern") {
                addThemeVariants(ButtonVariant.LUMO_PRIMARY)
                addClickShortcut(Key.ENTER)
                addClickListener { save() }
            }
        }

        binder.readBean(vendor)
    }

    private fun save() {
        try {
            binder.writeBean(vendor)
            close()
            callback(true)
        } catch (_: ValidationException) {
        }
    }
}

suspend fun editVendorDialog(vendor: VendorItem) =
    suspendCancellableCoroutine {
        val dialog = EditVendorDialog(vendor) { result -> it.resume(result) }
        it.invokeOnCancellation { dialog.close() }
        dialog.open()
    }