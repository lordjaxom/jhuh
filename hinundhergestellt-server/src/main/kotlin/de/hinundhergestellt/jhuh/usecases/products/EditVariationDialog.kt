package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.data.binder.ValidationException
import com.vaadin.flow.data.validator.BigDecimalRangeValidator
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.components.bigDecimalField
import de.hinundhergestellt.jhuh.components.bind
import de.hinundhergestellt.jhuh.components.binder
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.footer
import de.hinundhergestellt.jhuh.components.formLayout
import de.hinundhergestellt.jhuh.components.header
import de.hinundhergestellt.jhuh.components.formHeader
import de.hinundhergestellt.jhuh.components.textField
import de.hinundhergestellt.jhuh.components.toProperty
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.math.BigDecimal
import kotlin.coroutines.resume

private class EditVariationDialog(
    private val artooVariation: ArtooMappedVariation,
    private val syncVariant: SyncVariant?,
    private val callback: (EditVariationResult?) -> Unit
) : Dialog() {

    private val artooBinder = binder<ArtooMappedVariation>()
    private val syncBinder = binder<SyncVariant>()

    init {
        width = "500px"
        headerTitle = "Variation bearbeiten"

        header {
            button(VaadinIcon.CLOSE) {
                addThemeVariants(ButtonVariant.LUMO_TERTIARY)
                addClickShortcut(Key.ESCAPE)
                addClickListener { close(); callback(null) }
            }
        }
        formLayout {
            setAutoResponsive(true)
            isExpandColumns = true
            isExpandFields = true

            formHeader("Stammdaten aus ready2order")
            textField("Name") {
                setWidthFull()
                bind(artooBinder)
                    .asRequired("Name darf nicht leer sein.")
                    .toProperty(ArtooMappedVariation::name)
                focus()
            }
        }
        formLayout {
            setAutoResponsive(true)
            isExpandColumns = true
            isExpandFields = true
            style.setMarginTop("var(--lumo-space-m)")

            formHeader("Zusätzliche Informationen für Shopify")
            bigDecimalField("Gewicht") {
                bind(syncBinder)
                    .asRequired("Gewicht darf nicht leer sein.")
                    .withValidator(BigDecimalRangeValidator("Gewicht muss größer als 0,5 sein.", BigDecimal("0.5"), BigDecimal("99999")))
                    .toProperty(SyncVariant::weight)
            }
            button("* AI")
        }
        footer {
            button("Speichern") {
                addThemeVariants(ButtonVariant.LUMO_PRIMARY)
                addClickListener { save() }
            }
        }

        artooBinder.readBean(artooVariation)
        syncBinder.readBean(syncVariant)
    }

    private fun save() {
        try {
            val artoo = if (artooBinder.hasChanges()) artooVariation.also { artooBinder.writeBean(it) } else null
            val sync = if (syncBinder.hasChanges()) syncVariant.also { syncBinder.writeBean(it) } else null
            close()
            callback(EditVariationResult(artoo, sync))
        } catch (_: ValidationException) {
        }
    }
}

class EditVariationResult(
    val artoo: ArtooMappedVariation?,
    val sync: SyncVariant?
)

// TODO move to another file
suspend inline fun <T : Dialog, R> suspendableDialog(crossinline dialogProvider: ((R) -> Unit) -> T) =
    suspendCancellableCoroutine {
        val dialog = dialogProvider { result -> it.resume(result) }
        it.invokeOnCancellation { dialog.close() }
        dialog.open()
    }

suspend fun editVariation(artooVariation: ArtooMappedVariation, syncVariant: SyncVariant?) =
    suspendableDialog { EditVariationDialog(artooVariation, syncVariant, it) }