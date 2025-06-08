package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.icon.VaadinIcon

class GridActionButton(
    icon: VaadinIcon,
    clickListener: () -> Unit
) : Button(icon.create().apply { setSize("24px") }) {

    init {
        width = "30px"
        height = "24px"
        addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE)
        addClickListener { clickListener() }
    }
}