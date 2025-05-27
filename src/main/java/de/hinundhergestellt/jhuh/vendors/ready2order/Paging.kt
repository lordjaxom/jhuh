package de.hinundhergestellt.jhuh.vendors.ready2order

fun <T> pageAll(function: (Int) -> List<T>) =
    generateSequence(1) { it + 1 }
        .map { function(it) }
        .takeWhile { it.isNotEmpty() }
        .flatten()
