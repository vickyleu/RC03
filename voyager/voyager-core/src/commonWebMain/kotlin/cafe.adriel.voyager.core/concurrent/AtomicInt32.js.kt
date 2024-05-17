package cafe.adriel.voyager.core.concurrent

//import kotlinx.atomicfu.atomic  //TODO atomicfu n

public actual class AtomicInt32 actual constructor(initialValue: Int) {
//    private val delegate = atomic(initialValue)
    public actual fun getAndIncrement(): Int {
        return 0//delegate.incrementAndGet()
    }
}
