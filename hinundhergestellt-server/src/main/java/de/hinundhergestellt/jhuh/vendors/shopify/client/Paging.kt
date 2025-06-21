package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.PageInfo
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

internal fun <T> pageAll(after: String? = null, function: suspend (String?) -> Pair<List<T>, PageInfo>) =
    flow {
        var lastPage: PageInfo? = null
        do {
            val (result, page) = function(lastPage?.endCursor ?: after)
            emitAll(result.asFlow())
            lastPage = page
        } while (page.hasNextPage)
    }