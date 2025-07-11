@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.core

import java.util.function.IntFunction
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

typealias DirtyMaker = () -> Unit

internal class DirtyTrackedMutableList<V>(
    private val tracked: MutableList<V>,
    private val dirtyMaker: DirtyMaker,
) : MutableList<V> by tracked {

    override fun add(element: V) = dirtyMaker.makeDirtyIf { tracked.add(element) }
    override fun add(index: Int, element: V) = dirtyMaker.makeDirty { tracked.add(index, element) }
    override fun addAll(index: Int, elements: Collection<V>) = dirtyMaker.makeDirtyIf { tracked.addAll(index, elements) }
    override fun addAll(elements: Collection<V>) = dirtyMaker.makeDirtyIf { tracked.addAll(elements) }
    override fun clear() = if (isNotEmpty()) dirtyMaker.makeDirty { tracked.clear() } else Unit
    override fun remove(element: V) = dirtyMaker.makeDirtyIf { tracked.remove(element) }
    override fun removeAll(elements: Collection<V>) = dirtyMaker.makeDirtyIf { tracked.removeAll(elements) }
    override fun removeAt(index: Int) = dirtyMaker.makeDirty { tracked.removeAt(index) }
    override fun retainAll(elements: Collection<V>) = dirtyMaker.makeDirtyIf { tracked.retainAll(elements) }
    override fun set(index: Int, element: V) = dirtyMaker.makeDirty { tracked.set(index, element) }
    override fun subList(fromIndex: Int, toIndex: Int) = DirtyTrackedMutableList(tracked.subList(fromIndex, toIndex), dirtyMaker)
    override fun listIterator() = DirtyTrackedMutableListIterator(tracked.listIterator(), dirtyMaker)
    override fun listIterator(index: Int) = DirtyTrackedMutableListIterator(tracked.listIterator(index), dirtyMaker)
    override fun iterator() = listIterator()

    @Deprecated("This declaration is redundant in Kotlin and might be removed soon.")
    override fun <T : Any?> toArray(generator: IntFunction<Array<out T>>): Array<out T> {
        return super.toArray(generator)
    }
}

internal class DirtyTrackedMutableListIterator<V>(
    private val tracked: MutableListIterator<V>,
    private val dirtyMaker: DirtyMaker,
) : MutableListIterator<V> by tracked {

    override fun add(element: V) = dirtyMaker.makeDirty { tracked.add(element) }
    override fun remove() = dirtyMaker.makeDirty { tracked.remove() }
    override fun set(element: V) = dirtyMaker.makeDirty { tracked.set(element) }
}

private fun DirtyMaker.makeDirtyIf(block: () -> Boolean): Boolean {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        callsInPlace(this@makeDirtyIf, InvocationKind.AT_MOST_ONCE)
    }
    return block() && run { this(); true }
}

private fun <R> DirtyMaker.makeDirty(block: () -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        callsInPlace(this@makeDirty, InvocationKind.EXACTLY_ONCE)
    }
    return block().also { this() }
}