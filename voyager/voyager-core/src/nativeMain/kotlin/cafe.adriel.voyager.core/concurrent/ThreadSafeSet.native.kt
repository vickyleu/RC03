package cafe.adriel.voyager.core.concurrent

import kotlinx.atomicfu.locks.SynchronizedObject

public actual   class ThreadSafeSet<T>(
    syncObject: SynchronizedObject,
    delegate: MutableSet<T>
) : MutableSet<T>, ThreadSafeMutableCollection<T>(syncObject, delegate) {
    public actual constructor() : this(delegate = mutableSetOf())
    public constructor(delegate: MutableSet<T>) : this(SynchronizedObject(), delegate)

    actual override  val size: Int
        get() = super.size

    actual override  fun contains(element: T): Boolean {
        return super.contains(element)
    }

    actual override  fun containsAll(elements: Collection<T>): Boolean {
        return super.containsAll(elements)
    }

    actual override  fun isEmpty(): Boolean {
        return super.isEmpty()
    }

    actual override  fun iterator(): MutableIterator<T> {
        return super.iterator()
    }

    actual override  fun add(element: T): Boolean {
        return super.add(element)
    }

    actual override  fun addAll(elements: Collection<T>): Boolean {
        return super.addAll(elements)
    }

    actual override  fun clear() {
        super.clear()
    }

    actual override  fun remove(element: T): Boolean {
        return super.remove(element)
    }

    actual override  fun removeAll(elements: Collection<T>): Boolean {
        return super.removeAll(elements)
    }

    actual override  fun retainAll(elements: Collection<T>): Boolean {
        return super.retainAll(elements)
    }
}
