package tw.nekomimi.nekogram.utils

import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.AlertDialog
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

/**
 * 一些基于语言特性的全局函数
 */

fun <T, R> receive(getter: T.() -> R) = Receiver(getter)

class Receiver<T, R>(val getter: T.() -> R) {

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): R {

        return getter(thisRef as T)

    }

}

fun <T : Any, R> receiveLazy(initializer: T.() -> R) = LazyReceiver(initializer)

class LazyReceiver<T : Any, R>(val initializer: T.() -> R) {

    private val isInitialized = HashMap<T, Unit>()
    private val cache = HashMap<T, R>()

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any, property: KProperty<*>): R {

        if (isInitialized[thisRef] != null) return cache[thisRef] as R

        synchronized(this) {

            if (isInitialized[thisRef] != null) return cache[thisRef] as R

            return initializer(thisRef as T).apply {

                cache[thisRef] = this

                isInitialized[thisRef] = Unit

            }

        }

    }

}

operator fun <F> KProperty0<F>.getValue(thisRef: Any?, property: KProperty<*>): F = get()
operator fun <F> KMutableProperty0<F>.setValue(thisRef: Any?, property: KProperty<*>, value: F) = set(value)

operator fun AtomicBoolean.getValue(thisRef: Any?, property: KProperty<*>): Boolean = get()
operator fun AtomicBoolean.setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) = set(value)

operator fun AtomicInteger.getValue(thisRef: Any?, property: KProperty<*>): Int = get()
operator fun AtomicInteger.setValue(thisRef: Any?, property: KProperty<*>, value: Int) = set(value)

operator fun AtomicLong.getValue(thisRef: Any?, property: KProperty<*>): Long = get()
operator fun AtomicLong.setValue(thisRef: Any?, property: KProperty<*>, value: Long) = set(value)

operator fun <T> AtomicReference<T>.getValue(thisRef: Any?, property: KProperty<*>): T = get()
operator fun <T> AtomicReference<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)

fun AlertDialog.uUpdate(message: String) = AndroidUtilities.runOnUIThread { setMessage(message) }
fun AlertDialog.uDismiss() = AndroidUtilities.runOnUIThread { dismiss() }
