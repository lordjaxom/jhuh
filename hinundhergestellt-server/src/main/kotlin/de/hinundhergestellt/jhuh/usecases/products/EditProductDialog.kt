package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.textfield.BigDecimalField
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.binder.ValidationException
import com.vaadin.flow.dom.Style
import com.wontlost.ckeditor.VaadinCKEditor
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncTechnicalDetail
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.components.ReorderableGridField
import de.hinundhergestellt.jhuh.components.TagsTextField
import de.hinundhergestellt.jhuh.components.VaadinCoroutineScope
import de.hinundhergestellt.jhuh.components.bigDecimalField
import de.hinundhergestellt.jhuh.components.bind
import de.hinundhergestellt.jhuh.components.binder
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.checkbox
import de.hinundhergestellt.jhuh.components.comboBox
import de.hinundhergestellt.jhuh.components.div
import de.hinundhergestellt.jhuh.components.footer
import de.hinundhergestellt.jhuh.components.formLayout
import de.hinundhergestellt.jhuh.components.header
import de.hinundhergestellt.jhuh.components.htmlEditor
import de.hinundhergestellt.jhuh.components.itemLabelGenerator
import de.hinundhergestellt.jhuh.components.lightHeaderSpan
import de.hinundhergestellt.jhuh.components.reorderableGridField
import de.hinundhergestellt.jhuh.components.setColspan
import de.hinundhergestellt.jhuh.components.tagsTextField
import de.hinundhergestellt.jhuh.components.textField
import de.hinundhergestellt.jhuh.components.toProperty
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.springframework.stereotype.Component
import java.math.BigDecimal

private class EditProductDialog(
    private val service: EditProductService,
    applicationScope: CoroutineScope,
    private val artooProduct: ArtooMappedProduct,
    private val syncProduct: SyncProduct,
    private val callback: (EditProductResult?) -> Unit
) : Dialog() {

    private val artooBinder = binder<ArtooMappedProduct>()
    private val syncBinder = binder<SyncProduct>()

    private val descriptionTextField: TextField
    private val descriptionHtmlEditor: VaadinCKEditor
    private val technicalDetailsGridField: ReorderableGridField
    private val vendorComboBox: ComboBox<SyncVendor>
    private val inheritedTagsTextField: TagsTextField
    private val additionalTagsTextField: TagsTextField
    private val weightBigDecimalField: BigDecimalField?

    private val vaadinScope = VaadinCoroutineScope(this, applicationScope, null)

    init {
        width = "1000px"
        headerTitle = "Produkt bearbeiten"

        header {
            button(VaadinIcon.CLOSE) {
                addThemeVariants(ButtonVariant.LUMO_TERTIARY)
                addClickShortcut(Key.ESCAPE)
                addClickListener { close(); callback(null) }
            }
        }

        formLayout {
            setResponsiveSteps(
                FormLayout.ResponsiveStep("0", 1),
                FormLayout.ResponsiveStep("900px", 3)
            )

            lightHeaderSpan("Stammdaten aus ready2order") {
                this@formLayout.setColspan(3)
                style.setAlignSelf(Style.AlignSelf.FLEX_START)
            }
            textField("Name (Kassenoberfläche)") {
                this@formLayout.setColspan(1)
                bind(artooBinder)
                    .asRequired("Name darf nicht leer sein.")
                    .toProperty(ArtooMappedProduct::name)
                focus()
            }
            descriptionTextField = textField("Beschreibung (Titel in Shopify)") {
                this@formLayout.setColspan(2)
                bind(artooBinder)
                    .asRequired("Beschreibung darf nicht leer sein.")
                    .toProperty(ArtooMappedProduct::description)
            }
        }

        formLayout {
            setResponsiveSteps(
                FormLayout.ResponsiveStep("0", 1),
                FormLayout.ResponsiveStep("900px", 3)
            )

            lightHeaderSpan("Zusätzliche Informationen für Shopify") {
                this@formLayout.setColspan(3)
                style.setAlignSelf(Style.AlignSelf.FLEX_START)
            }
            div {
                this@formLayout.setColspan(2)
                style.setAlignSelf(Style.AlignSelf.FLEX_START)

                descriptionHtmlEditor = htmlEditor("Produktbeschreibung") {
                    height = "10em"
                    bind(syncBinder).toProperty(SyncProduct::descriptionHtml)
                }
                technicalDetailsGridField = reorderableGridField("Technische Daten") {
                    height = "10em"
                    setWidthFull()
                    bind(syncBinder).bind(
                        { it.technicalDetails.map { item -> ReorderableGridField.Item(item.name, item.value) } },
                        { target, value ->
                            target.technicalDetails.clear()
                            target.technicalDetails += value.mapIndexed { index, item -> SyncTechnicalDetail(item.name, item.value, index) }
                        }
                    )
                }
            }
            div {
                this@formLayout.setColspan(1)
                style.setAlignSelf(Style.AlignSelf.FLEX_START)

                checkbox("Synchronisieren?") {
                    bind(syncBinder).toProperty(SyncProduct::synced)
                }
                vendorComboBox = comboBox<SyncVendor>("Hersteller") {
                    isClearButtonVisible = true
                    itemLabelGenerator { it.name }
                    setWidthFull()
                    setItems(service.vendors)
                    addValueChangeListener { updateInheritedTags(it.oldValue?.name, it.value?.name) }
                    bind(syncBinder)
                        .asRequired("Hersteller darf nicht leer sein.")
                        .toProperty(SyncProduct::vendor)
                }
                textField("Produktart") {
                    setWidthFull()
                    addValueChangeListener { updateInheritedTags(it.oldValue, it.value) }
                    bind(syncBinder)
                        .asRequired("Produktart darf nicht leer sein.")
                        .toProperty(SyncProduct::type)
                }
                inheritedTagsTextField = tagsTextField("Vererbte Tags") {
                    setWidthFull()
                    isReadOnly = true
                    value = service.inheritedTags(syncProduct, artooProduct)
                }
                additionalTagsTextField = tagsTextField("Weitere Tags") {
                    setWidthFull()
                    bind(syncBinder).bind(
                        { it.tags },
                        { target, value -> target.tags.clear(); target.tags += value }
                    )
                }
                weightBigDecimalField =
                    if (artooProduct.hasOnlyDefaultVariant) {
                        bigDecimalField("Gewicht") {
                            bind(syncBinder)
                                .asRequired("Gewicht darf nicht leer sein.")
                                .withValidator({ it >= BigDecimal("0.5") }, "Gewicht darf nicht weniger als 0.5 sein.")
                                .bind(
                                    { it.variants[0].weight },
                                    { target, value -> target.variants[0].weight = value }
                                )
                        }
                    } else null
            }
        }
        footer {
            button("Werte ausfüllen") {
                addClickListener { fillInValues() }
            }
            button("Texte generieren") {
                addClickListener { generateTexts() }
            }
            button("Speichern") {
                addThemeVariants(ButtonVariant.LUMO_PRIMARY)
                addClickListener { save() }
            }
        }

        artooBinder.readBean(artooProduct)
        syncBinder.readBean(syncProduct)
    }

    private fun fillInValues() {
        val values = service.fillInValues(artooProduct)
        values.vendor?.also { vendorComboBox.value = it }
        values.description?.also { descriptionTextField.value = it }
        values.weight?.also { weightBigDecimalField?.value = it }
    }

    private fun generateTexts() = vaadinScope.launch {
        isEnabled = false
        try {
            val details = application { async { service.generateProductDetails(artooProduct, syncProduct) }.await() }
            descriptionHtmlEditor.value = details.descriptionHtml
            technicalDetailsGridField.value = details.technicalDetails.map { ReorderableGridField.Item(it.key, it.value) }
            additionalTagsTextField.addTags(details.tags.toSet() - inheritedTagsTextField.value)
        } finally {
            isEnabled = true
        }
    }

    private fun updateInheritedTags(oldValue: String?, newValue: String?) {
        oldValue?.also { inheritedTagsTextField.value -= service.sanitizeTag(it) }
        newValue?.also { inheritedTagsTextField.value += service.sanitizeTag(it) }
    }

    private fun save() {
        try {
            if (!artooBinder.validate().isOk or !syncBinder.validate().isOk) return

            val result = EditProductResult(
                artooProduct.takeIf { artooBinder.hasChanges() }?.also { artooBinder.writeBean(it) },
                syncProduct.takeIf { syncBinder.hasChanges() }?.also { syncBinder.writeBean(it) }
            )
            callback(result)
            close()
        } catch (_: ValidationException) {
        }
    }
}

class EditProductResult(
    val artoo: ArtooMappedProduct?,
    val sync: SyncProduct?
)

typealias EditProduct = suspend (artooProduct: ArtooMappedProduct, syncProduct: SyncProduct) -> EditProductResult?

@Component
class EditProductDialogFactory(
    private val service: EditProductService,
    private val applicationScope: CoroutineScope,
) : EditProduct {

    override suspend fun invoke(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct) =
        suspendableDialog { EditProductDialog(service, applicationScope, artooProduct, syncProduct, it) }
}