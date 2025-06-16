package de.hinundhergestellt.jhuh.usecases.vendors

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode

class EditVendorDialog(
    private val vendor: VendorItem,
    private val saveListener: () -> Unit
) : Dialog() {

    private val nameTextField = TextField()
    private val saveButton = Button()
    private val emailTextField = TextField()
    private val addressTextArea = TextArea()

    init {
        width = "500px"
        headerTitle = "Hersteller bearbeiten"

        val closeButton = Button(VaadinIcon.CLOSE.create()).apply {
            addThemeVariants(ButtonVariant.LUMO_TERTIARY)
            addClickListener { close() }
        }
        header.add(closeButton)

        val bodyLayout = VerticalLayout().apply {
            isSpacing = false
            isPadding = false
        }
        add(bodyLayout)

        nameTextField.apply {
            label = "Bezeichnung"
            value = vendor.name
            valueChangeMode = ValueChangeMode.EAGER
            setWidthFull()
            addValueChangeListener { validateInputs() }
            focus()
        }
        bodyLayout.add(nameTextField)

        emailTextField.apply {
            label = "E-Mail"
            value = vendor.email
            valueChangeMode = ValueChangeMode.EAGER
            setWidthFull()
            addValueChangeListener { validateInputs() }
        }
        bodyLayout.add(emailTextField)

        addressTextArea.apply {
            label = "Adresse"
            value = vendor.address
            maxLength = 255
            minRows = 5
            maxRows = 5
            valueChangeMode = ValueChangeMode.EAGER
            setWidthFull()

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

            fun updateHelperText() { helperText = "${value.length}/${maxLength}" }
            addValueChangeListener { updateHelperText(); validateInputs() }
            updateHelperText()
        }
        bodyLayout.add(addressTextArea)

        saveButton.apply {
            text = "Speichern"
            addThemeVariants(ButtonVariant.LUMO_PRIMARY)
            addClickShortcut(Key.ENTER)
            addClickListener { save() }
        }
        footer.add(saveButton)

        validateInputs()
        open()
    }

    private fun validateInputs() {
        saveButton.isEnabled = sequenceOf(nameTextField, emailTextField, addressTextArea).all { it.value.trim().isNotEmpty() }
    }

    private fun save() {
        isEnabled = false

        vendor.name = nameTextField.value.trim()
        vendor.email = emailTextField.value.trim()
        vendor.address = addressTextArea.value.trim()

        saveListener()
        close()
    }
}