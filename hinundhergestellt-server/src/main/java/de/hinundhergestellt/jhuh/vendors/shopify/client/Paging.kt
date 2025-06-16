package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.PageInfo

internal fun <T> pageAll(function: (String?) -> Pair<List<T>, PageInfo>) = sequence {
    var lastPage: PageInfo? = null
    do {
        val (result, page) = function(lastPage?.endCursor)
        yieldAll(result)
        lastPage = page
    } while (page.hasNextPage)
}