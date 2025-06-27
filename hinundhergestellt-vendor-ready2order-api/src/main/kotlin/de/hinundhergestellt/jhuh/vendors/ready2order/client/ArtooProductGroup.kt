package de.hinundhergestellt.jhuh.vendors.ready2order.client

import de.hinundhergestellt.jhuh.core.DirtyTracker
import de.hinundhergestellt.jhuh.core.HasDirtyTracker
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductgroupsGet200ResponseInner
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductgroupsPostRequest

open class UnsavedArtooProductGroup(
    name: String,
    description: String,
    shortcut: String,
    active: Boolean,
    parent: Int? = null,
    sortIndex: Int = 0,
    type: ArtooProductGroupType = ArtooProductGroupType.STANDARD
): HasDirtyTracker {

    override val dirtyTracker = DirtyTracker()

    var name by dirtyTracker.track(name)
    var description by dirtyTracker.track(description)
    var shortcut by dirtyTracker.track(shortcut)
    var active by dirtyTracker.track(active)
    var parent by dirtyTracker.track(parent)
    var sortIndex by dirtyTracker.track(sortIndex)
    var type by dirtyTracker.track(type)

    override fun toString() =
        "UnsavedArtooProductGroup(name='$name', description='$description')"

    internal fun toProductgroupsPostRequest() =
        ProductgroupsPostRequest(
            productgroupName = name,
            productgroupDescription = description,
            productgroupShortcut = shortcut,
            productgroupActive = active,
            productgroupParent = parent,
            productgroupSortIndex = sortIndex,
            productgroupTypeId = type.id
        )
}

class ArtooProductGroup : UnsavedArtooProductGroup {

    val id: Int

    internal constructor(group: ProductgroupsGet200ResponseInner) : super(
        group.productgroupName!!,
        group.productgroupDescription!!,
        group.productgroupShortcut!!,
        group.productgroupActive!!,
        group.productgroupParent,
        group.productgroupSortIndex!!,
        ArtooProductGroupType.valueOf(group.productgroupTypeId)
    ) {
        id = group.productgroupId!!
    }

    override fun toString() =
        "ArtooProductGroup(id=$id, name='$name', description='$description')"
}

enum class ArtooProductGroupType(
    val id: Int?
) {
    FAVOURITES(null),
    VARIANTS(3),
    VARIATIONS(5),
    INGREDIENTS(6),
    STANDARD(7),
    DISCOUNTS(8),
    VOUCHERS(10);

    companion object {
        fun valueOf(id: Int?) = entries.find { it.id == id }!!
    }
}