package de.hinundhergestellt.jhuh.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

suspend fun <T> Iterable<T>.forEachIndexedParallel(threads: Int, block: suspend (Int, T) -> Unit) {
    coroutineScope {
        val channel = Channel<Pair<Int, T>>(Channel.RENDEZVOUS)
        val jobs = List(threads) { launch { channel.consumeEach { (index, item) -> block(index, item) } } }
        forEachIndexed { index, item -> channel.send(Pair(index, item)) }
        channel.close()
        jobs.joinAll()
    }
}

suspend fun <T> Iterable<T>.forEachParallel(threads: Int, block: suspend (T) -> Unit) =
    forEachIndexedParallel(threads) { _, item -> block(item) }

suspend inline fun <T, reified R> Collection<T>.mapIndexedParallel(threads: Int, crossinline block: suspend (Int, T) -> R) =
    coroutineScope {
        if (isEmpty()) return@coroutineScope emptyList()
        val results = arrayOfNulls<R>(size)
        val channel = Channel<Pair<Int, T>>(Channel.RENDEZVOUS)
        val jobs = List(threads.coerceAtMost(size)) {
            launch { channel.consumeEach { (index, item) -> results[index] = block(index, item) } }
        }
        forEachIndexed { index, item -> channel.send(Pair(index, item)) }
        channel.close()
        jobs.joinAll()
        results.map { it as R }
    }

suspend inline fun <T, reified R> Collection<T>.mapParallel(threads: Int, crossinline block: suspend (T) -> R) =
    mapIndexedParallel(threads) { _, item -> block(item) }