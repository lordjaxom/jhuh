package de.hinundhergestellt.jhuh.labels

import com.vaadin.flow.component.ItemLabelGenerator
import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.menubar.MenuBar
import com.vaadin.flow.component.menubar.MenuBarVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.component.textfield.TextFieldVariant
import com.vaadin.flow.data.provider.Query
import com.vaadin.flow.data.value.ValueChangeMode
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.service.ready2order.ArtooDataStore
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedVariation
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.asStream

private val logger = KotlinLogging.logger {}

@Route
@PageTitle("Etiketten erstellen")
class LabelGeneratorView(
    private val artooDataStore: ArtooDataStore
) : VerticalLayout() {

    private val articleComboBox = ComboBox<ArtooMappedVariation>()
    private val countTextField = TextField()
    private val addButton = Button()

    init {
        setSizeFull()
        width = "1170px"
        style.setMargin("0 auto")

        configureHeader()
        configureInputs()
    }

    private fun configureHeader() {
        val menuBar = MenuBar()
        menuBar.setWidthFull()
        menuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED)
        menuBar.addItem("Erstellen")
        add(menuBar)
    }

    private fun configureInputs() {
        articleComboBox.label = "Artikel"
        articleComboBox.itemLabelGenerator = ItemLabelGenerator { comboItemLabel(it) }
        articleComboBox.setWidthFull()
        articleComboBox.setItems { fetchArticles(it) }
        articleComboBox.addValueChangeListener { verifyInputs() }
        articleComboBox.focus()

        countTextField.label = "Anzahl"
        countTextField.value = "0"
        countTextField.allowedCharPattern = "[0-9]"
        countTextField.maxLength = 5
        countTextField.valueChangeMode = ValueChangeMode.EAGER
        countTextField.isAutoselect = true
        countTextField.addThemeVariants(TextFieldVariant.LUMO_ALIGN_RIGHT)
        countTextField.addValueChangeListener { verifyInputs() }

        addButton.text = "Hinzufügen"
        addButton.isEnabled = false
        addButton.addClickShortcut(Key.ENTER)
        addButton.addClickListener { addLabels() }

        val layout = HorizontalLayout(articleComboBox, countTextField, addButton)
        layout.alignItems = FlexComponent.Alignment.END
        layout.setWidthFull()
        add(layout)
    }

    private fun comboItemLabel(it: ArtooMappedVariation): String {
        return "${it.barcode} - ${it.name}"
    }

    private fun verifyInputs() {
        val articleSelected = articleComboBox.value != null
        val countValid = countTextField.value.toIntOrNull()?.let { it > 0 } ?: false
        addButton.isEnabled = articleSelected && countValid
    }

    private fun addLabels() {
        articleComboBox.value = null
        countTextField.value = "0"
        articleComboBox.focus()
    }

    private fun fetchArticles(query: Query<ArtooMappedVariation, String>): Stream<ArtooMappedVariation> {
        val filter = query.filter.getOrNull() ?: ""
        if (filter.isEmpty()) {
            return Stream.empty<ArtooMappedVariation>()
                .skip(query.offset.toLong()) // to fulfuill Query contracts?!
                .limit(query.limit.toLong())
        }

        return artooDataStore.findAllProducts()
            .flatMap { it.variations }
            .filter { it.name.contains(filter, ignoreCase = true) || it.barcode?.startsWith(filter) ?: false }
            .asStream()
            .skip(query.offset.toLong())
            .limit(query.limit.toLong())
    }
}