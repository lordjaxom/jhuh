@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.core

import java.util.function.IntFunction
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class DirtyTrackedMutableList<V>(
    private val tracked: MutableList<V>,
    private val dirtyMaker: () -> Unit,
) : MutableList<V> by tracked {

    override fun add(element: V) = makeDirtyIf { tracked.add(element) }
    override fun add(index: Int, element: V) = makeDirty { tracked.add(index, element) }
    override fun addAll(index: Int, elements: Collection<V>) = makeDirtyIf { tracked.addAll(index, elements) }
    override fun addAll(elements: Collection<V>) = makeDirtyIf { tracked.addAll(elements) }
    override fun clear() = if (isNotEmpty()) makeDirty { tracked.clear() } else Unit
    override fun remove(element: V) = makeDirtyIf { tracked.remove(element) }
    override fun removeAll(elements: Collection<V>) = makeDirtyIf { tracked.removeAll(elements) }
    override fun removeAt(index: Int) = makeDirty { tracked.removeAt(index) }
    override fun retainAll(elements: Collection<V>) = makeDirtyIf { tracked.retainAll(elements) }
    override fun set(index: Int, element: V) = makeDirty { tracked.set(index, element) }
    override fun subList(fromIndex: Int, toIndex: Int) = DirtyTrackedMutableList(tracked.subList(fromIndex, toIndex), dirtyMaker)

    override fun listIterator(): MutableListIterator<V> {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): MutableListIterator<V> {
        TODO("Not yet implemented")
    }

    override fun iterator(): MutableIterator<V> {
        TODO("Not yet implemented")
    }

    @Deprecated("This declaration is redundant in Kotlin and might be removed soon.")
    override fun <T : Any?> toArray(generator: IntFunction<Array<out T>>): Array<out T> {
        return super.toArray(generator)
    }

    private fun makeDirtyIf(block: () -> Boolean): Boolean {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return block() && run { dirtyMaker(); true }
    }

    private fun <R> makeDirty  (block: () -> R): R {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return block().also { dirtyMaker() }
    }
}