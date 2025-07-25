package de.hinundhergestellt.jhuh.core

import java.util.concurrent.CopyOnWriteArrayList

class RemoveProtectedMutableList<E>(
    private val list: MutableList<E>,
) : AbstractMutableList<E>() {

    override fun get(index: Int) = list[index]
    override fun add(index: Int, element: E) = list.add(index, element)
    override fun set(index: Int, element: E) = list.set(index, element)
    override val size get() = list.size

    override fun removeAt(index: Int): E {
        throw UnsupportedOperationException("removeAt")
    }
}

fun <E> CopyOnWriteArrayList<E>.asRemoveProtectedMutableList() = RemoveProtectedMutableList(this)