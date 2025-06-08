package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.component.textfield.TextFieldVariant
import com.vaadin.flow.data.value.ValueChangeMode

class CountTextField: TextField() {

    init {
        label = "Anzahl"
        placeholder = "0"
        allowedCharPattern = "[0-9]"
        maxLength = 5
        valueChangeMode = ValueChangeMode.EAGER
        isAutoselect = true
        width = "5em"
        addThemeVariants(TextFieldVariant.LUMO_ALIGN_RIGHT)
    }
}