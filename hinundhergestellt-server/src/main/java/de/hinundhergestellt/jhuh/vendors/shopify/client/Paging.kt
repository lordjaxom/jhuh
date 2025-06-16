package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.shopify.admin.types.PageInfo

internal fun <T> pageAll(function: (String?) -> Pair<Sequence<T>, PageInfo>) = sequence {
    var lastPage: PageInfo? = null
    do {
        val (result, page) = function(lastPage?.endCursor)
        yieldAll(result)
        lastPage = page
    } while (page.hasNextPage)
}