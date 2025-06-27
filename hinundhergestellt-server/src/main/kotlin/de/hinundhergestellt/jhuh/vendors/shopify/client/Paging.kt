package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.PageInfo
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

internal fun <T> pageAll(pageInfo: PageInfo? = null, function: suspend (String?) -> Pair<List<T>, PageInfo>) =
    flow {
        var lastPage = pageInfo
        while (lastPage == null || lastPage.hasNextPage) {
            val (result, page) = function(lastPage?.run { endCursor!! })
            emitAll(result.asFlow())
            lastPage = page
        }
    }