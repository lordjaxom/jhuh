package de.hinundhergestellt.jhuh.vendors.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.api.ProductGroupApi
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductgroupsGet200ResponseInner
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductgroupsPostRequest
import org.springframework.lang.Nullable

class ArtooProductGroup {
    private var value: ProductgroupsGet200ResponseInner

    constructor(
        name: String,
        description: String,
        shortcut: String,
        active: Boolean,
        parent: Int?,
        sortIndex: Int,
        typeId: Int
    ) {
        value = ProductgroupsGet200ResponseInner()
        value.productgroupName = name
        value.productgroupDescription = description
        value.productgroupShortcut = shortcut
        value.productgroupActive = active
        value.productgroupParent = parent
        value.productgroupSortIndex = sortIndex
        value.productgroupTypeId = typeId
    }

    internal constructor(value: ProductgroupsGet200ResponseInner) {
        this.value = value
    }

    val id: Int
        get() = value.productgroupId

    val name: String
        get() = value.productgroupName

    val description: String
        get() = value.productgroupDescription

    val parent: Int
        get() = value.productgroupParent ?: 0

    val typeId: Int
        get() = value.productgroupTypeId ?: 0

    fun getPath(productGroups: List<ArtooProductGroup>): String {
        return sequenceOf(getParentPath(productGroups), name)
            .filterNotNull()
            .joinToString("/")
    }

    @Nullable
    fun getParentPath(productGroups: List<ArtooProductGroup>): String? {
        if (parent == 0) {
            return null
        }
        return productGroups.asSequence()
            .first { it.id == parent }
            .getPath(productGroups)
    }

    fun save(api: ProductGroupApi) {
        val request = toPostRequest()
        if (value.productgroupId != null) {
            value = api.productgroupsIdPut(value.productgroupId, request)
        } else {
            value = api.productgroupsPost(request)
        }
    }

    override fun toString(): String {
        return value.toString()
    }

    private fun toPostRequest(): ProductgroupsPostRequest {
        val request = ProductgroupsPostRequest()
        request.productgroupName = value.productgroupName
        request.productgroupDescription = value.productgroupDescription
        request.productgroupShortcut = value.productgroupShortcut
        request.productgroupActive = value.productgroupActive
        request.productgroupParent = value.productgroupParent
        request.productgroupSortIndex = value.productgroupSortIndex
        request.productgroupTypeId = value.productgroupTypeId
        return request
    }
}
