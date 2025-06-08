package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.ItemLabelGenerator
import com.vaadin.flow.component.combobox.ComboBox
import org.springframework.stereotype.Component
import kotlin.streams.asStream

class ArticleComboBox(
    private val service: ArticleComboBoxService
) : ComboBox<Article>() {

    init {
        label = "Artikel"
        placeholder = "Barcode oder Suchbegriff eingeben"
        itemLabelGenerator = ItemLabelGenerator { it.label }
        setWidthFull()
        setDataProvider(
            { filter, offset, limit -> service.fetch(filter).drop(offset).take(limit).asStream() },
            { filter -> service.fetch(filter).count() }
        )

        // @formatter:off
        element.executeJs("""
            const input = this.inputElement
            input.addEventListener('keydown', function(e) {
                if (e.key === 'Enter') {
                    this.dispatchEvent(new CustomEvent('enter-pressed', {
                            detail: { inputValue: input.value },
                            bubbles: true
                    }));
                }            
            });
            """.trimIndent())
        // @formatter:on

        val enterListener = element.addEventListener("enter-pressed") { event ->
            val found = event.eventData.getString("event.detail.inputValue")
                ?.takeIf { it.toULongOrNull(10) != null }
                ?.let { service.fetch(it).take(2).toList() }
            if (found?.size == 1) {
                isOpened = false
                value = found[0]
            }
        }
        enterListener.addEventData("event.detail.inputValue")
    }
}

@Component
class ArticleComboBoxFactory(
    private val service: ArticleComboBoxService
) : () -> ArticleComboBox {

    override fun invoke() = ArticleComboBox(service)
}