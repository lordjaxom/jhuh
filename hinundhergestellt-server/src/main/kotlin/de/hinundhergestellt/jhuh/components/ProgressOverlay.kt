@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.HasText
import com.vaadin.flow.component.html.Div
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class ProgressOverlay : Div(), HasText {

    private val progressText: Div

    init {
        isVisible = false
        classNames += "progress-overlay"

        div {
            classNames += "progress-box"
            div { classNames += "progress-spinner" }
            progressText = div { classNames += "progress-text" }
        }
    }

    override fun getText(): String? = progressText.text
    override fun setText(text: String?) { progressText.text = text }
}

inline fun HasComponents.progressOverlay(block: ProgressOverlay.() -> Unit = {}): ProgressOverlay {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(ProgressOverlay(), block)
}
