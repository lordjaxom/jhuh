package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.accordion.AccordionPanel

fun AccordionPanel.accordionSummary(title: String, description: String) {
    summary = single {
        verticalLayout {
            themeList -= arrayOf("spacing", "padding")
            span(title) {
                style.setColor("var(--lumo-header-text-color)")
            }
            span(description) {
                style.setMarginTop("var(--lumo-space-xs)")
                style.setFontSize("var(--lumo-font-size-xxs)")
            }
        }
    }
}