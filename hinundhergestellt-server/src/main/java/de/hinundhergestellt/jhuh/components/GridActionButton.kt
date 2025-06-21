package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.contextmenu.ContextMenu
import com.vaadin.flow.component.html.Hr
import com.vaadin.flow.component.icon.VaadinIcon

open class GridActionButton(
    icon: VaadinIcon,
    clickListener: () -> Unit = {}
) : Button(icon.create().apply { setSize("24px") }) {

    init {
        width = "30px"
        height = "24px"
        addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE)
        addClickListener { clickListener() }
    }
}

class MoreGridActionButton : GridActionButton(VaadinIcon.ELLIPSIS_DOTS_H) {

    private val contextMenu = ContextMenu()

    init {
        contextMenu.target = this
        contextMenu.isOpenOnClick = true
    }

    fun addItem(text: String, action: () -> Unit): Unit =
        run { contextMenu.addItem(text) { action() } }

    fun addDivider(): Unit =
        run { contextMenu.addItem(Hr()) }
}
