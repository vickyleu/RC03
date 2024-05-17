package cafe.adriel.voyager.core.concurrent

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized

public actual  class ThreadSafeMap<K, V>(
    private val delegate: MutableMap<K, V>
) : MutableMap<K, V> {
    public actual constructor() : this(delegate = mutableMapOf())

    private val syncObject = SynchronizedObject()

    @OptIn(InternalCoroutinesApi::class)
    actual override  val size: Int
        get() = synchronized(syncObject) { delegate.size }

    @OptIn(InternalCoroutinesApi::class)
    actual override  fun containsKey(key: K): Boolean {
        return synchronized(syncObject) { delegate.containsKey(key) }
    }

    @OptIn(InternalCoroutinesApi::class)
    actual override  fun containsValue(value: V): Boolean {
        return synchronized(syncObject) { delegate.containsValue(value) }
    }

    @OptIn(InternalCoroutinesApi::class)
    actual override  fun get(key: K): V? {
        return synchronized(syncObject) { delegate[key] }
    }

    @OptIn(InternalCoroutinesApi::class)
    actual override  fun isEmpty(): Boolean {
        return synchronized(syncObject) { delegate.isEmpty() }
    }

    actual override  val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = delegate.entries
    actual override  val keys: MutableSet<K>
        get() = delegate.keys
    actual override  val values: MutableCollection<V>
        get() = delegate.values

    @OptIn(InternalCoroutinesApi::class)
    actual override  fun clear() {
        synchronized(syncObject) { delegate.clear() }
    }

    @OptIn(InternalCoroutinesApi::class)
    actual override  fun put(key: K, value: V): V? {
        return synchronized(syncObject) { delegate.put(key, value) }
    }

    @OptIn(InternalCoroutinesApi::class)
    actual override  fun putAll(from: Map<out K, V>) {
        synchronized(syncObject) { delegate.putAll(from) }
    }

    @OptIn(InternalCoroutinesApi::class)
    actual override  fun remove(key: K): V? {
        return synchronized(syncObject) { delegate.remove(key) }
    }
}
