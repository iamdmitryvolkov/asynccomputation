package core
import java.util.ArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Future

// TODO: migrate to gradle

// TODO: in/out everywhere

// Main Objects

class Value<T>(obj : T) : Calculation<T>(ValueComputation(obj)) {

} // TODO: wrap to static func

class If<T>(private val comp: Calculation<T>, private val condition : (T) -> Boolean) {

    fun<D> thn(func : (T) -> D) : IfThen<T, D> {
        return IfThen(comp, condition, func)
    }
}

class IfThen<T, D>(private val comp: Calculation<T>, private val condition: (T) -> Boolean, private val then : (T) -> D) {

    fun els(func : (T) -> D) : Calculation<D> {
        return comp.then { if (condition(it)) then(it) else func(it) }
    }
}

class NewThreadExecutor : Executor {

    override fun execute(command: Runnable?) = Thread(command).start()

}

open class EmptySwitch<T, E>(private val comp: Calculation<T>, private val selector: (T) -> E) {

    fun <D> case(value: E, func: (T) -> D) : Switch<T, E, D> {
        val result : Switch<T, E, D> = Switch(comp, selector)
        return result.case(value, func)
    }

    fun <D> default(func: (T) -> D) = comp.then(func)
}

class Switch<T, E, D>(private val comp: Calculation<T>, private val selector: (T) -> E) {

    private val cases : MutableMap<E, (T) -> D> = mutableMapOf()

    fun case(value: E, func: (T) -> D) : Switch<T, E, D> = this.apply { cases.put(value, func) }

    fun default(func: (T) -> D) = comp.then({ cases.getOrDefault(selector(it), func).invoke(it) })
}

// Computable

open class Calculation<T> internal constructor(private val computation: Computation<Any?, T>) {

    private fun <D> next(func : (T) -> D) : Calculation<D> {
        return Calculation(computation.add(MethodComputationWrapper(func)))
    }

    fun on(executor: ComputationExecutor) : Calculation<T> {
        return Calculation(computation.add(ExecutorComputation(executor)))
    }

    fun on(executor: Executor) : Calculation<T> {
        return on(ComputationExecutorImpl(executor))
    }

    fun compute(): Future<T> = computation.execute(null, ComputationExecutorImpl(NewThreadExecutor()))

    fun compute(callback: (T) -> Unit) {
        then(callback).compute()
    }

    fun <E> then(func : (T) -> E) : Calculation<E> {
        return next { func(it) }
    }

    fun check(condition : (T) -> Boolean) = If(this, condition)

    fun <D> switch(func : (T) -> D) = EmptySwitch(this, func)
}

// Extensions

fun <T, E, D : Iterable<T>> Calculation<D>.map(func : (T) -> E) : Calculation<List<E>> {
    return then { it.map(func) }
} // TODO: add array version

fun <T, D : Iterable<T>> Calculation<D>.joinValues(vararg objects: T) : Calculation<List<T>> {
    return then {
        ArrayList<T>().also {
            it.addAll(it)
            it.addAll(objects)
        }
    }
}

fun <T, D : Iterable<T>> Calculation<D>.joinValues(objects: List<T>) : Calculation<List<T>> {
    return then {
        ArrayList<T>().also {
            it.addAll(it)
            it.addAll(objects)
        }
    }
}

fun <T> IfThen<T, T>.endIf() : Calculation<T> {
    return els { it }
}
