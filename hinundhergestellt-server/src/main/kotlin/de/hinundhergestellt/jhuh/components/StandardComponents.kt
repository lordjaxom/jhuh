@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.html.Span
import com.wontlost.ckeditor.Config
import com.wontlost.ckeditor.Constants
import com.wontlost.ckeditor.Constants.Toolbar
import com.wontlost.ckeditor.VaadinCKEditor
import com.wontlost.ckeditor.VaadinCKEditorBuilder
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@VaadinDsl
fun (@VaadinDsl HasComponents).htmlEditor(label: String, block: (@VaadinDsl VaadinCKEditor).() -> Unit = {}): VaadinCKEditor {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    val editor = VaadinCKEditorBuilder()
        .with {
            it.editorType = Constants.EditorType.CLASSIC
            it.config = Config().apply {
                setEditorToolBar(
                    arrayOf(
                        Toolbar.heading, Toolbar.pipe,
                        Toolbar.bold, Toolbar.italic, Toolbar.underline, Toolbar.fontColor, Toolbar.pipe,
                        Toolbar.alignment, Toolbar.pipe,
                        Toolbar.link, Toolbar.pipe,
                        Toolbar.bulletedList, Toolbar.numberedList, Toolbar.indent, Toolbar.pipe,
                        Toolbar.sourceEditing
                    )
                )
            }
        }
        .createVaadinCKEditor()
        .apply {
            this.label = label
            isRequiredIndicatorVisible = true
            style.setMargin("0")
        }
    return init(editor, block)
}

@VaadinDsl
fun (@VaadinDsl HasComponents).lightHeaderSpan(text: String, block: (@VaadinDsl Span).() -> Unit = {}): Span {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    val span = Span(text).apply {
        style.setMarginTop("var(--lumo-space-m)")
        style.setColor("var(--lumo-secondary-text-color)")
        style.setFontSize("var(--lumo-font-size-l)")
        style.setFontWeight(500)
    }
    return init(span, block)
}
