import core.*
import java.io.PrintStream
import java.util.concurrent.Executors
val executorOne = ComputationExecutorImpl(Executors.newFixedThreadPool(1))
val executorTwo = ComputationExecutorImpl(Executors.newFixedThreadPool(1))
val executorThree = ComputationExecutorImpl(Executors.newFixedThreadPool(1))


fun printCurrentThread() {
    System.out.println(Thread.currentThread().id)
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
        .add(consoleLogger()).add(threadLogger()).add(ExecutorComputation(executorTwo))
        .add(consoleLogger()).add(threadLogger()).add(ExecutorComputation(executorThree))
        .add(consoleLogger()).add(threadLogger())



fun main(args: Array<String>) {
    printCurrentThread()
    val res = computation.execute(20, executorThree).get()
    computation.eval(20)
    System.out.println("bye")

    //val e = ValueComputation(3, )
}