package de.hinundhergestellt.jhuh.vendors.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductgroupsGet200ResponseInner
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductgroupsPostRequest

open class UnsavedArtooProductGroup(
    var name: String,
    var description: String,
    var shortcut: String,
    var active: Boolean,
    var parent: Int? = null,
    var sortIndex: Int = 0,
    var type: ArtooProductGroupType = ArtooProductGroupType.STANDARD
) {
    override fun toString() =
        "UnsavedArtooProductGroup(name='$name', description='$description')"

    internal fun toProductgroupsPostRequest() =
        ProductgroupsPostRequest().also {
            it.productgroupName = name
            it.productgroupDescription = description
            it.productgroupShortcut = shortcut
            it.productgroupActive = active
            it.productgroupParent = parent
            it.productgroupSortIndex = sortIndex
            it.productgroupTypeId = type.id
        }
}

class ArtooProductGroup : UnsavedArtooProductGroup {

    val id: Int

    internal constructor(group: ProductgroupsGet200ResponseInner) : super(
        group.productgroupName,
        group.productgroupDescription,
        group.productgroupShortcut,
        group.productgroupActive,
        group.productgroupParent,
        group.productgroupSortIndex,
        ArtooProductGroupType.valueOf(group.productgroupTypeId)
    ) {
        id = group.productgroupId
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
        fun valueOf(id: Int?) = ArtooProductGroupType.entries.find { it.id == id }!!
    }
}