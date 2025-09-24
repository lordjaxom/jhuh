package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.Text
import com.vaadin.flow.component.button.Button
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
import de.hinundhergestellt.jhuh.components.span
import de.hinundhergestellt.jhuh.components.tagsTextField
import de.hinundhergestellt.jhuh.components.text
import de.hinundhergestellt.jhuh.components.textField
import de.hinundhergestellt.jhuh.components.toProperty
import de.hinundhergestellt.jhuh.components.verticalLayout
import de.hinundhergestellt.jhuh.core.isNullOrZero
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import kotlinx.coroutines.async
import org.springframework.stereotype.Component
import java.math.BigDecimal

private class EditProductDialog(
    private val service: EditProductService,
    private val vaadinScope: VaadinCoroutineScope<*>,
    private val artooProduct: ArtooMappedProduct,
    private val syncProduct: SyncProduct,
    private val callback: (EditProductResult?) -> Unit
) : Dialog() {

    private val artooBinder = binder<ArtooMappedProduct>()
    private val syncBinder = binder<SyncProduct>()

    private val descriptionTextField: TextField
    private val descriptionHtmlEditor: VaadinCKEditor
    private val technicalDetailsGridField: ReorderableGridField
    private val productImagesText: Text
    private val variantImagesText: Text?
    private val vendorComboBox: ComboBox<SyncVendor>
    private val productTypeTextField: TextField
    private val inheritedTagsTextField: TagsTextField
    private val additionalTagsTextField: TagsTextField
    private val weightBigDecimalField: BigDecimalField?
    private val autoFillButton: Button
    private val downloadImagesButton: Button
    private val generateTextsButton: Button

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
                addValueChangeListener { updateImageTexts(); validateActions() }
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
                verticalLayout {
                    isPadding = true
                    style["gap"] = "0"
                    span {
                        productImagesText = text("0 Produktbilder")
                    }
                    span {
                        variantImagesText =
                            if (!artooProduct.hasOnlyDefaultVariant) text("0 Variantenbilder")
                            else null
                    }
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
                productTypeTextField = textField("Produktart") {
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
                        { it.tags.toSet() }, // create copy otherwise value is it.tags in setter
                        { target, value -> target.tags.clear(); target.tags += value }
                    )
                }
                if (!artooProduct.hasOnlyDefaultVariant) {
                    textField("Optionsname") {
                        setWidthFull()
                        bind(syncBinder)
                            .asRequired("Optionsname darf nicht leer sein.")
                            .toProperty(SyncProduct::optionName)
                    }
                }
                weightBigDecimalField =
                    if (artooProduct.hasOnlyDefaultVariant) {
                        bigDecimalField("Gewicht (Gramm)") {
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
            autoFillButton = button("Aus Katalog ausfüllen") {
                addClickListener { autoFillInValues() }
            }
            downloadImagesButton = button("Bilder herunterladen") {
                addClickListener { downloadImages() }
            }
            generateTextsButton = button("Texte generieren") {
                addClickListener { generateTexts() }
            }
            button("Speichern") {
                addThemeVariants(ButtonVariant.LUMO_PRIMARY)
                addClickListener { save() }
            }
        }

        artooBinder.readBean(artooProduct)
        syncBinder.readBean(syncProduct)
        updateImageTexts()

        validateActions()
    }

    private fun updateImageTexts() {
        val syncImages = service.findSyncImages(artooProduct, descriptionTextField.value)
        productImagesText.text = "${syncImages.count { it.variantSku == null }} Produktbilder"
        variantImagesText?.text = "${syncImages.count { it.variantSku != null }} Variantenbilder"
    }

    private fun validateActions() {
        val canFillInValues = service.canFillInValues(artooProduct)
        val needsFillInValues = vendorComboBox.value == null ||
                descriptionTextField.value.isNullOrEmpty() ||
                (weightBigDecimalField != null && weightBigDecimalField.value.isNullOrZero())
        autoFillButton.isEnabled = canFillInValues && needsFillInValues
        downloadImagesButton.isEnabled = service.canDownloadImages(artooProduct, descriptionTextField.value)
    }

    private fun autoFillInValues() {
        val values = service.fillInValues(artooProduct)
        if (vendorComboBox.value == null && values.vendor != null) vendorComboBox.value = values.vendor
        if (descriptionTextField.value.isNullOrEmpty() && values.description != null) descriptionTextField.value = values.description
        if (weightBigDecimalField != null && weightBigDecimalField.value.isNullOrZero() && values.weight != null)
            weightBigDecimalField.value = values.weight
    }

    private fun downloadImages() {
        vaadinScope.launchWithReporting {
            application { service.downloadImages(artooProduct, descriptionTextField.value, ::report) }
            updateImageTexts()
        }
    }

    private fun generateTexts() {
        vaadinScope.launchWithReporting {
            if (descriptionHtmlEditor.value.isNullOrEmpty()) {
                report("Generiere Produkttexte mit AI...")
                val generated = application {
                    async { service.generateProductTexts(artooProduct, syncProduct, descriptionTextField.value) }.await()
                }
                descriptionHtmlEditor.value = generated.descriptionHtml
            }
            if (technicalDetailsGridField.value.isNullOrEmpty() ||
                additionalTagsTextField.value.isEmpty() ||
                productTypeTextField.value.isNullOrEmpty()
            ) {
                report("Generiere Produktdetails mit AI...")
                val generated = application {
                    async { service.generateProductDetails(artooProduct, syncProduct, descriptionTextField.value) }.await()
                }
                if (technicalDetailsGridField.value.isNullOrEmpty())
                    technicalDetailsGridField.value = generated.technicalDetails.map { ReorderableGridField.Item(it.key, it.value) }
                if (additionalTagsTextField.value.isEmpty())
                    additionalTagsTextField.addTags(generated.tags.toSet() - inheritedTagsTextField.value)
                if (productTypeTextField.value.isNullOrEmpty())
                    productTypeTextField.value = generated.productType
            }
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
            // handled by framework
        }
    }
}

class EditProductResult(
    val artoo: ArtooMappedProduct?,
    val sync: SyncProduct?
)

typealias EditProduct = suspend (
    artooProduct: ArtooMappedProduct,
    syncProduct: SyncProduct,
    vaadinScope: VaadinCoroutineScope<*>
) -> EditProductResult?

@Component
class EditProductDialogFactory(
    private val service: EditProductService
) : EditProduct {

    override suspend fun invoke(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct, vaadinScope: VaadinCoroutineScope<*>) =
        suspendableDialog { EditProductDialog(service, vaadinScope, artooProduct, syncProduct, it) }
}