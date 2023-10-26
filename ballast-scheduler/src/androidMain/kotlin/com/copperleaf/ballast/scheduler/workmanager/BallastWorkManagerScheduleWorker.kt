package com.copperleaf.ballast.scheduler.workmanager

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import com.copperleaf.ballast.scheduler.SchedulerAdapter
import com.copperleaf.ballast.scheduler.executor.ScheduleExecutor
import com.copperleaf.ballast.scheduler.internal.RegisteredSchedule
import com.copperleaf.ballast.scheduler.schedule.dropHistory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.toJavaDuration

/**
 * This is a WorkManager job which executes on each tick of the registered schedule, then enqueues the next Instant
 * that the job should rerun. It also is responsible for accessing the target VM that the Input should be sent to on
 * each task tick.
 */
@Suppress("UNCHECKED_CAST")
public class BallastWorkManagerScheduleWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    final override suspend fun doWork(): Result = coroutineScope {
        val workManager = WorkManager.getInstance(applicationContext)
        val adapterClassQualifiedName: String = inputData.getString(DATA_ADAPTER_CLASS)!!
        val scheduleKey: String = inputData.getString(DATA_KEY)!!
        val adapterClass: Class<SchedulerAdapter<*, *, *>> =
            Class.forName(adapterClassQualifiedName) as Class<SchedulerAdapter<*, *, *>>
        val adapter: SchedulerAdapter<*, *, *> = adapterClass.getConstructor().newInstance()
        val registeredSchedule: RegisteredSchedule<*, *, *> =
            adapter.getRegisteredSchedules().single { it.key == scheduleKey }

        Log.d("BallastWorkManager", "running periodic job at '${registeredSchedule.key}'")
        val initialInstant = Instant.fromEpochMilliseconds(inputData.getLong(DATA_INITIAL_INSTANT, 0))
        val latestInstant = Instant.fromEpochMilliseconds(inputData.getLong(DATA_LATEST_INSTANT, 0))

        when (registeredSchedule.delayMode) {
            ScheduleExecutor.DelayMode.FireAndForget -> {
                BallastWorkManagerScheduleWorker.scheduleNextInvocation(
                    workManager = workManager,
                    adapter = adapter,
                    registeredSchedule = registeredSchedule,
                    initialInstant = initialInstant,
                    latestInstant = latestInstant,
                )
                dispatchWork(
                    adapter = adapter,
                    registeredSchedule = registeredSchedule,
                    deferred = null,
                )
            }

            ScheduleExecutor.DelayMode.Suspend -> {
                val deferred = CompletableDeferred<Unit>()
                dispatchWork(
                    adapter = adapter,
                    registeredSchedule = registeredSchedule,
                    deferred = deferred,
                )
                deferred.await()
                BallastWorkManagerScheduleWorker.scheduleNextInvocation(
                    workManager = workManager,
                    adapter = adapter,
                    registeredSchedule = registeredSchedule,
                    initialInstant = initialInstant,
                    latestInstant = latestInstant,
                )
            }
        }

        Result.success()
    }

    private suspend fun dispatchWork(
        adapter: SchedulerAdapter<*, *, *>,
        registeredSchedule: RegisteredSchedule<*, *, *>,
        deferred: CompletableDeferred<Unit>?
    ) {
        check(adapter is Function1<*, *>) {
            "adapter must be Function1<I, Unit>"
        }

        invokeWith(
            adapter,
            registeredSchedule.scheduledInput()
        )

        deferred?.complete(Unit)
    }

    private suspend fun <P1, R> invokeWith(
        fn: Function1<P1, R>,
        input: Any
    ) {
        withContext(Dispatchers.Main) {
            fn.invoke(input as P1)
        }
    }

    public companion object {
        public const val DATA_ADAPTER_CLASS: String = "DATA_ADAPTER_CLASS"
        public const val DATA_KEY: String = "DATA_KEY"
        public const val DATA_INITIAL_INSTANT: String = "DATA_INITIAL_INSTANT"
        public const val DATA_LATEST_INSTANT: String = "DATA_LATEST_INSTANT"
        public const val SCHEDULE_NAME_PREFIX: String = "ballast_schedule_key_"

        internal suspend fun setupSchedule(
            workManager: WorkManager,
            adapter: SchedulerAdapter<*, *, *>,
            registeredSchedule: RegisteredSchedule<*, *, *>,
            initialInstant: Instant,
            latestInstant: Instant,
        ) {
            try {
                val workInfo = workManager
                    .getWorkInfos(WorkQuery.fromUniqueWorkNames(listOf(registeredSchedule.key)))
                    .awaitInternal()
                if (workInfo.isNotEmpty()) {
                    // if there is already a scheduled task at this key, just return
                    return
                }
            } catch (e: Exception) {
                // ignore
            }

            Log.d("BallastWorkManager", "Creating periodic work schedule at '${registeredSchedule.key}'")
            val nextInstant: Instant? = registeredSchedule.schedule
                .dropHistory(initialInstant, latestInstant)
                .firstOrNull()
            if (nextInstant == null) {
                // schedule has completed, don't schedule another task
                Log.d("BallastWorkManager", "periodic work at '${registeredSchedule.key}' completed")
                return
            }

            val delayAmount = nextInstant - Clock.System.now()
            val adapterClassName = adapter.javaClassName

            Log.d(
                "BallastWorkManager",
                "Scheduling next periodic work at '${registeredSchedule.key}' (to trigger at in $delayAmount at $nextInstant)"
            )
            workManager
                .beginUniqueWork(
                    /* uniqueWorkName = */ registeredSchedule.key,
                    /* existingWorkPolicy = */ ExistingWorkPolicy.REPLACE,
                    /* work = */ OneTimeWorkRequestBuilder<BallastWorkManagerScheduleWorker>()
                        .setInputData(
                            Data.Builder()
                                .putString(DATA_ADAPTER_CLASS, adapterClassName)
                                .putString(DATA_KEY, registeredSchedule.key)
                                .putLong(DATA_INITIAL_INSTANT, initialInstant.toEpochMilliseconds())
                                .putLong(DATA_LATEST_INSTANT, nextInstant.toEpochMilliseconds())
                                .build()
                        )
                        .addTag("ballast")
                        .addTag("schedule")
                        .addTag(adapterClassName)
                        .addTag("$SCHEDULE_NAME_PREFIX${registeredSchedule.key}")
                        .setInitialDelay(delayAmount.toJavaDuration())
                        .build()
                )
                .enqueue()
        }

        internal fun scheduleNextInvocation(
            workManager: WorkManager,
            adapter: SchedulerAdapter<*, *, *>,
            registeredSchedule: RegisteredSchedule<*, *, *>,
            initialInstant: Instant,
            latestInstant: Instant,
        ) {
            Log.d("BallastWorkManager", "Scheduling periodic work at '${registeredSchedule.key}'")
            val nextInstant: Instant? = registeredSchedule.schedule
                .dropHistory(initialInstant, latestInstant)
                .firstOrNull()
            if (nextInstant == null) {
                // schedule has completed, don't schedule another task
                Log.d("BallastWorkManager", "periodic work at '${registeredSchedule.key}' completed")
                return
            }

            val delayAmount = nextInstant - Clock.System.now()
            val adapterClassName = adapter.javaClassName

            Log.d(
                "BallastWorkManager",
                "Scheduling next periodic work at '${registeredSchedule.key}' (to trigger at in $delayAmount at $nextInstant)"
            )
            workManager
                .beginUniqueWork(
                    /* uniqueWorkName = */ registeredSchedule.key,
                    /* existingWorkPolicy = */ ExistingWorkPolicy.REPLACE,
                    /* work = */ OneTimeWorkRequestBuilder<BallastWorkManagerScheduleWorker>()
                        .setInputData(
                            Data.Builder()
                                .putString(DATA_ADAPTER_CLASS, adapterClassName)
                                .putString(DATA_KEY, registeredSchedule.key)
                                .putLong(DATA_INITIAL_INSTANT, initialInstant.toEpochMilliseconds())
                                .putLong(DATA_LATEST_INSTANT, nextInstant.toEpochMilliseconds())
                                .build()
                        )
                        .addTag("ballast")
                        .addTag("schedule")
                        .addTag(adapterClassName)
                        .addTag("$SCHEDULE_NAME_PREFIX${registeredSchedule.key}")
                        .setInitialDelay(delayAmount.toJavaDuration())
                        .build()
                )
                .enqueue()
        }
    }
}
