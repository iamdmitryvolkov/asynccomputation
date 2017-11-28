package core

import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Computation core classes and interfaces
 * TODO: decrease visibility modifiers
 *
 * @author Dmitry Volkov
 */


interface Computation<in T, E> {

    fun eval(arg : T) : E

    fun execute(arg: T, executor: ComputationExecutor) : Future<E>

    fun <D> add(nextComputation: Computation<E, D>) : Computation<T, D>
}

interface ComputationExecutor {

    fun addTask(task: Runnable)

    fun executePendingTask()

}

private val currentExecutor : ThreadLocal<ComputationExecutorImpl?> = ThreadLocal()

private fun <T> waitForExecution(task : Future<T>) : T {
    while (!task.isDone) {
        currentExecutor.get()?.executePendingTask()
    }

    return task.get()
}

class ComputationExecutorImpl(private val executor: Executor) : ComputationExecutor {

    private val taskQueue : ArrayDeque<ComputationTask> = ArrayDeque()

    override fun addTask(task: Runnable) { // TODO: blocking deque?
        val computationTask = ComputationTask(task)
        taskQueue.addLast(computationTask)
        executor.execute(computationTask)
    }

    override fun executePendingTask() {
        if (taskQueue.isNotEmpty()) {
            val newTask = taskQueue.pollFirst()
            newTask.runInternal()
        } else {
            Thread.yield()
        }
    }

    private inner class ComputationTask(private val originalRunnable: Runnable) : Runnable {

        @Volatile
        private var isStarted = AtomicBoolean(false)

        override fun run() {
            currentExecutor.set(this@ComputationExecutorImpl)
            runInternal()
            currentExecutor.set(null)
        }

        fun runInternal() {
            if (!isStarted.getAndSet(true)) {
                taskQueue.remove(this)
                originalRunnable.run()
            }
        }

    }

}

abstract class FutureTaskComputation<in T, E> : Computation<T, E> {

    override fun execute(arg: T, executor: ComputationExecutor) : Future<E> {
        return FutureTask({
            eval(arg)
        }).also {
            executor.addTask(it)
        }
    }
}


abstract class BaseComputation<in T, E> : FutureTaskComputation<T, E>() {

    override fun <D> add(nextComputation: Computation<E, D>) : Computation<T, D> {
        return if (nextComputation is ExecutorComputation<*>) {
            ExecutorComputationWrapper({ arg -> eval(arg) as D }, nextComputation.executor)
        } else {
            MethodComputationWrapper({ arg -> nextComputation.eval(eval(arg)) })
        }
    }
}

private class ExecutorComputationWrapper<in T, E>(private val method : (T) -> E,
                                                  private val executor : ComputationExecutor)
    : FutureTaskComputation<T, E>() {

    override fun eval(arg: T): E = method(arg)

    override fun <D> add(nextComputation: Computation<E, D>) : Computation<T, D> {
        return MethodComputationWrapper({ arg -> waitForExecution(nextComputation.execute(eval(arg), executor)) })
    }
}

open class MethodComputationWrapper<in T, E>(private val method : (T) -> E) : BaseComputation<T, E>() {

    override fun eval(arg: T): E = method(arg)

}

class ExecutorComputation<T>(internal val executor : ComputationExecutor) : BaseComputation<T, T>() {

    override fun eval(arg: T): T = arg

}

class ValueComputation<T>(private val obj: T) : BaseComputation<Any?, T>() {

    override fun eval(arg: Any?): T = obj

}