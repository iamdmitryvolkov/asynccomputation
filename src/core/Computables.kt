import core.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Future


// TODO: in/out everywhere

// TODO: swap computable/computation


// Computations

class ValueComputation<T>(private val obj: T) : BaseComputation<Any?, T>() {

    override fun eval(arg: Any?): T = obj

}

// Computable

open class Computable<T>(internal val computation: Computation<Any?, T>) {

    internal fun <D> next(func : (T) -> D) : Computable<D> { // TODO: make private
        return Computable(computation.add(MethodComputationWrapper(func)))
    }

    // TODO: this executor must be stopper after end of program
    fun compute(): Future<T> = computation.execute(null, ComputationExecutorImpl(Executors.newSingleThreadExecutor()))

}


// Main Objects

class Value<T>(obj : T) : Computable<T>(ValueComputation(obj)) // TODO: wrap to static func

class If<T>(private val comp: Computable<T>, private val condition : (T) -> Boolean) {

    fun<D> thn(func : (T) -> D) : IfThen<T, D> {
        return IfThen(comp, condition, func)
    }
}

class IfThen<T, D>(private val comp: Computable<T>, private val condition: (T) -> Boolean, private val then : (T) -> D) {

    fun els(func : (T) -> D) : Computable<D> {
        return comp.next { if (condition(it)) then(it) else func(it) }
    }

}

// Extensions

fun <T, E> Computable<T>.then(func : (T) -> E) : Computable<E> {
    return next { func(it) }
}

fun <T> Computable<T>.check(condition : (T) -> Boolean) : If<T> = If(this, condition)

fun <T, E, D : Iterable<T>> Computable<D>.map(func : (T) -> E) : Computable<List<E>> {
    return then { it.map(func) }
}

fun <T> Computable<T>.on(executor: Executor) : Computable<T> {
    return on(ComputationExecutorImpl(executor))
}

fun <T> Computable<T>.on(executor: ComputationExecutor) : Computable<T> {
    return Computable(computation.add(ExecutorComputation(executor)))
}

fun main() {

    val e = Value(listOf(3))
    e.map { it > 2 }.then { it.first() }.check { it }.thn { 3 }.els { 5 }

}