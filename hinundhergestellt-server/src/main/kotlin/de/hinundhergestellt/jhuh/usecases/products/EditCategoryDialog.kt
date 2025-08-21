package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.data.binder.ValidationException
import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategory
import de.hinundhergestellt.jhuh.components.TagsTextField
import de.hinundhergestellt.jhuh.components.bind
import de.hinundhergestellt.jhuh.components.binder
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.footer
import de.hinundhergestellt.jhuh.components.formLayout
import de.hinundhergestellt.jhuh.components.header
import de.hinundhergestellt.jhuh.components.tagsTextField
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedCategory
import org.springframework.stereotype.Component
import kotlin.collections.plusAssign

private class EditCategoryDialog(
    private val mappingService: MappingService,
    private val artooCategory: ArtooMappedCategory,
    private val syncCategory: SyncCategory,
    private val callback: (EditCategoryResult?) -> Unit
) : Dialog() {

    private val inheritedTagsTextField: TagsTextField
    private val additionalTagsTextField: TagsTextField

    private val syncBinder = binder<SyncCategory>()

    init {
        width = "500px"
        headerTitle = "Kategorie bearbeiten"

        header {
            button(VaadinIcon.CLOSE) {
                addThemeVariants(ButtonVariant.LUMO_TERTIARY)
                addClickShortcut(Key.ESCAPE)
                addClickListener { close(); callback(null) }
            }
        }

        formLayout {
            inheritedTagsTextField = tagsTextField("Vererbte Tags") {
                setWidthFull()
                isReadOnly = true
                value = mappingService.inheritedTags(artooCategory)
            }
            additionalTagsTextField = tagsTextField("Weitere Tags") {
                setWidthFull()
                bind(syncBinder).bind(
                    { it.tags },
                    { target, value -> target.tags.clear(); target.tags += value }
                )
            }
        }

        footer {
            button("Speichern") {
                addThemeVariants(ButtonVariant.LUMO_PRIMARY)
                addClickListener { save() }
            }
        }

        syncBinder.readBean(syncCategory)
    }

    private fun save() {
        try {
            if (!syncBinder.validate().isOk) return

            val result = EditCategoryResult(
                syncCategory.takeIf { syncBinder.hasChanges() }?.also { syncBinder.writeBean(it) }
            )
            callback(result)
            close()
        } catch (_: ValidationException) {
        }
    }
}

class EditCategoryResult(
    val sync: SyncCategory?
)

typealias EditCategory = suspend (artooCategory: ArtooMappedCategory, syncCategory: SyncCategory) -> EditCategoryResult?

@Component
class EditCategoryDialogFactory(
    private val mappingService: MappingService
) : EditCategory {

    override suspend fun invoke(artooCategory: ArtooMappedCategory, syncCategory: SyncCategory): EditCategoryResult? =
        suspendableDialog { EditCategoryDialog(mappingService, artooCategory, syncCategory, it) }
}
