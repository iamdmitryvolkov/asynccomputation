package core
import java.util.ArrayList
import java.util.concurrent.Executor

// Main Objects

class Value<T>(obj : T) : Calculation<T>(ValueComputation(obj))

class If<out T>(private val comp: Calculation<T>, private val condition : (T) -> Boolean) {

    fun<D> thn(func : (T) -> D) : IfThen<T, D> = IfThen(comp, condition, func)
}

class IfThen<out T, D>(private val comp: Calculation<T>, private val condition: (T) -> Boolean,
                       private val then : (T) -> D) {

    fun els(func : (T) -> D) = comp.then { if (condition(it)) then(it) else func(it) }
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

    fun case(value: E, func: (T) -> D) = this.apply { cases.put(value, func) }

    fun default(func: (T) -> D) = comp.then({ cases.getOrDefault(selector(it), func).invoke(it) })
}

// Calculation

open class Calculation<T> internal constructor(private val computation: Computation<Any?, T>) {

    private fun <D> next(func : (T) -> D) = Calculation(computation.add(MethodComputationWrapper(func)))

    fun on(executor: ComputationExecutor) = Calculation(computation.add(ExecutorComputation(executor)))

    fun on(executor: Executor) = on(ComputationExecutorImpl(executor))

    fun compute() = computation.execute(null, ComputationExecutorImpl(NewThreadExecutor()))

    fun compute(callback: (T) -> Unit) {
        then(callback).compute()
    }

    fun <E> then(func : (T) -> E) = next { func(it) }

    fun check(condition : (T) -> Boolean) = If(this, condition)

    fun <D> switch(func : (T) -> D) = EmptySwitch(this, func)

    fun <D> every(vararg funcs : (T) -> D) = then { value -> funcs.map { it(value) } }
}

// Extensions

fun <T, E, D : Iterable<T>> Calculation<D>.map(func : (T) -> E) = then { it.map(func) }

fun <T, D : Iterable<T>> Calculation<D>.joinValues(vararg objects : T) : Calculation<List<T>> {
    return then {
        ArrayList<T>().also {
            it.addAll(it)
            it.addAll(objects)
        }
    }
}

fun <T, D : Iterable<T>> Calculation<D>.joinValues(objects:  List<T>) : Calculation<List<T>> {
    return then {
        ArrayList<T>().also {
            it.addAll(it)
            it.addAll(objects)
        }
    }
}

fun <T, D : Iterable<T>> Calculation<D>.first() = then { it.first() }

fun <T, D : Iterable<T>> Calculation<D>.last() = then { it.last() }

fun <T, D : Iterable<T>> Calculation<D>.filter(func : (T) -> Boolean) = then { it.filter(func) }

fun <T, E, D : Iterable<T>> Calculation<D>.flatMap(func : (T) -> Iterable<E>) = then { it.flatMap(func) }

fun <T, E, D : Iterable<T>> Calculation<D>.apply(vararg funcs : (T) -> E) : Calculation<List<E>> {
    return then {
        val funcsCount = funcs.size
        it.mapIndexed { index, elem ->
            funcs[index % funcsCount](elem)
        }
    }
}

fun <T, E, D : Iterable<T>> Calculation<D>.fold(initial : E, func : (E, T) -> E) = then { it.fold(initial, func) }

fun <T, E, D : Iterable<T>> Calculation<D>.zip(collection: Iterable<E>) = then { it.zip(collection) }

fun <T, E, D : Iterable<T>> Calculation<D>.zip(collection: Array<E>) = then { it.zip(collection) }

fun <T, E, D : List<Pair<T, E>>> Calculation<D>.unzip() = then { it.unzip() }

fun <T, D : Iterable<Iterable<T>>> Calculation<D>.zip() : Calculation<List<List<T>>> {
    return then {
        base ->
        if (base.count() == 0) {
            listOf(listOf<T>())
        } else {
            val len = base.map { it.count() }.min()!!
            mutableListOf<List<T>>().also {
                for (i in 0 until len) {
                    it.add(mutableListOf<T>().apply {
                        for (j in 0 until len) {
                             add(base.elementAt(j).elementAt(i))
                        }
                    })
                }
            }
        }
    }
}

fun <T, E, D : Iterable<T>> Calculation<D>.any(func: (T) -> Boolean) = then { it.any(func) }

fun <T, E, D : Iterable<T>> Calculation<D>.all(func: (T) -> Boolean) = then { it.all(func) }

fun <T, D : Iterable<Iterable<T>>> Calculation<D>.joinValues() = flatMap { it }

fun <T, E, D : Pair<T, E>> Calculation<D>.fst() = then { it.first }

fun <T, E, D : Pair<T, E>> Calculation<D>.snd() = then { it.second }

fun <T, D : Pair<List<T>, List<T>>> Calculation<D>.merge() : Calculation<List<T>> {
    return then {
        pair ->
        ArrayList<T>().also {
            it.addAll(pair.first)
            it.addAll(pair.second)
        }
    }
}

fun <T> IfThen<T, T>.endIf() = els { it }

fun<T> value(obj : T) = Value(obj)