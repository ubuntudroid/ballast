package com.copperleaf.ballast.scheduler.workmanager

import com.copperleaf.ballast.scheduler.SchedulerAdapter
import com.copperleaf.ballast.scheduler.internal.RegisteredSchedule
import com.copperleaf.ballast.scheduler.internal.SchedulerAdapterScopeImpl
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun <I : Any, E : Any, S : Any> SchedulerAdapter<I, E, S>.getRegisteredSchedules(): List<RegisteredSchedule<*, *, *>> {
    val adapter = this
    val adapterScope = SchedulerAdapterScopeImpl<I, E, S>()

    with(adapter) {
        adapterScope.configureSchedules()
    }

    // cancel any running schedules which have the same keys as the newly requested schedules
    return adapterScope.schedules
}

internal val <T : Any> T.javaClassName: String get() = this::class.java.name

@Suppress("BlockingMethodInNonBlockingContext", "RedundantSamConstructor")
internal suspend inline fun <R : Any> ListenableFuture<R>.awaitInternal(): R {
    // Fast path
    if (isDone) {
        try {
            return get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
    return suspendCancellableCoroutine { cancellableContinuation ->
        addListener(
            Runnable {
                try {
                    cancellableContinuation.resume(get())
                } catch (throwable: Throwable) {
                    val cause = throwable.cause ?: throwable
                    when (throwable) {
                        is CancellationException -> cancellableContinuation.cancel(cause)
                        else -> cancellableContinuation.resumeWithException(cause)
                    }
                }
            },
            Executor {
                it.run()
            },
        )

        cancellableContinuation.invokeOnCancellation {
            cancel(false)
        }
    }
}
