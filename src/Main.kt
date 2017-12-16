import core.*
import java.io.PrintStream
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

val threadFactory = DaemonThreadFactory()
val executorOne = ComputationExecutorImpl(Executors.newFixedThreadPool(1, threadFactory))
val executorTwo = ComputationExecutorImpl(Executors.newFixedThreadPool(1, threadFactory))
val executorThree = ComputationExecutorImpl(Executors.newFixedThreadPool(1, threadFactory))


class DaemonThreadFactory : ThreadFactory {

    override fun newThread(r: Runnable?): Thread {
        return Thread(r).apply { isDaemon = true }
    }

}

fun printCurrentThread() {
    System.out.println("Thread:" + Thread.currentThread().id)
}

class LoggingComputation<T>(private val stream : PrintStream) : BaseComputation<T, T>() {

    override fun eval(arg: T): T {
        stream.println(arg.toString())
        return arg
    }

}

fun <T> consoleLogger() = LoggingComputation<T>(System.out)

fun <T> threadLogger() = MethodComputationWrapper<T, T>({ v ->
    printCurrentThread()
    v
})

val computation = MethodComputationWrapper({ v: Int -> 3 * v })
        .add(threadLogger()).add(threadLogger()).add(ExecutorComputation(executorTwo))
        .add(threadLogger()).add(threadLogger()).add(ExecutorComputation(executorThree))
        .add(threadLogger()).add(threadLogger())



fun main(args: Array<String>) {
    printCurrentThread()
    val res = computation.execute(20, executorThree).get()
    System.out.println("run sync")
    computation.eval(20)
    System.out.println("bye")

    val e = value(listOf(3))

    e.joinValues(listOf(3, 6, 7))
            .joinValues(3, 4, 7)
            .map { it > 2 }.then { it.first() }.check { it }.thn { 3} .els { 5 }
            .check { it != 5 }.thn { it * 5 }.endIf()
            .switch { it }.default { it * 3 }
            .switch { it / 9 }
            .case(1, { 111 })
            .case(2, { 222 })
            .case(3, { 333 })
            .case(4, { 444 })
            .case(5, { 555 })
            .default { 777 }
            .every({2 * it}, {3 * it})
            .then { it.max()!! }
            .every({2 * it}, {3 * it})
            .first()
            .every({2 * it}, {3 * it})
            .apply({7 + it })
            .filter { it > 2000 }
            .flatMap { it -> listOf(it) }
            .fold(0, { a, b -> maxOf(a, b) })
            .every({2 * it}, {3 * it})
            .zip(arrayOf(4, 5, 6))
            .map { listOf(it.first, it.second) }
            .zip()
            .zip()
            .map { it[0] to it[1] }
            .unzip()
            .fst()
            .last()
            .compute {
        System.out.print(it)
    }
}