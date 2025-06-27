@file:OptIn(ExperimentalCoroutinesApi::class)

package de.hinundhergestellt.jhuh.vendors.ready2order.client

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile

internal fun <T> pageAll(function: suspend (Int) -> List<T>) =
    generateSequence(1) { it + 1 }.asFlow()
        .map { function(it) }
        .takeWhile { it.isNotEmpty() }
        .flatMapConcat { it.asFlow() }
