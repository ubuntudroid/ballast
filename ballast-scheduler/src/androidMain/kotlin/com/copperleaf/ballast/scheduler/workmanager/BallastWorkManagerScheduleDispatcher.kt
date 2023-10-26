package com.copperleaf.ballast.scheduler.workmanager

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.copperleaf.ballast.scheduler.SchedulerAdapter
import com.copperleaf.ballast.scheduler.workmanager.BallastWorkManagerScheduleWorker.Companion.DATA_ADAPTER_CLASS
import com.copperleaf.ballast.scheduler.workmanager.BallastWorkManagerScheduleWorker.Companion.DATA_INITIAL_INSTANT
import com.copperleaf.ballast.scheduler.workmanager.BallastWorkManagerScheduleWorker.Companion.DATA_LATEST_INSTANT
import com.copperleaf.ballast.scheduler.workmanager.BallastWorkManagerScheduleWorker.Companion.SCHEDULE_NAME_PREFIX
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * This is a WorkManager job which takes a [SchedulerAdapter] and creates new WorkManager tasks for each registered
 * schedule. Those are represented by OneTimeJobs which schedule their next execution moment after each subsequent
 * execution.
 *
 * The newly scheduled jobs are instances of [BallastWorkManagerScheduleWorker] which run the current execution, and
 * then schedule the next execution.
 */
@Suppress("UNCHECKED_CAST")
public open class BallastWorkManagerScheduleDispatcher(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    protected open fun getAdapter(adapterClassName: String): SchedulerAdapter<*, *, *> {
        return (Class.forName(adapterClassName) as Class<SchedulerAdapter<*, *, *>>)
            .getConstructor()
            .newInstance()
    }

    final override suspend fun doWork(): Result = coroutineScope {
        Log.d("BallastWorkManager", "Handling work dispatch")
        val workManager = WorkManager.getInstance(applicationContext)

        val adapterClassName = inputData.getString(DATA_ADAPTER_CLASS)!!
        val initialInstant = Instant.fromEpochMilliseconds(inputData.getLong(DATA_INITIAL_INSTANT, 0))
        val latestInstant = Instant.fromEpochMilliseconds(inputData.getLong(DATA_LATEST_INSTANT, 0))

        // run the adapter to get the schedules which should run
        val adapter: SchedulerAdapter<*, *, *> = getAdapter(adapterClassName)
        val schedules = adapter.getRegisteredSchedules()

        // Make sure each registered schedule is set up
        schedules.forEach { schedule ->
            BallastWorkManagerScheduleWorker.setupSchedule(
                workManager = workManager,
                adapter = adapter,
                registeredSchedule = schedule,
                initialInstant = initialInstant,
                latestInstant = latestInstant,
            )
        }

        // remove schedules which are not part of the current adapter's schedule
        try {
            coroutineScope {
                val scheduledJobTags = listOf("ballast", "schedule", adapterClassName)
                val orphanedJobsForThisAdapter = workManager
                    .getWorkInfosByTag("ballast")
                    .awaitInternal()
                    .filter {
                        // get the WorkManager jobs which were created from this adapter
                        it.tags.containsAll(scheduledJobTags)
                    }
                    .map {
                        // the remaining tag should be the schedule's key, which
                        tags.single { it.startsWith(SCHEDULE_NAME_PREFIX) }.removePrefix(SCHEDULE_NAME_PREFIX)
                    }
                    .filter { scheduleName ->
                        // get the WorkManager jobs which were created from this adapter, but are not part of the
                        // currently-scheduled jobs
                        schedules.none { it.key == scheduleName }
                    }

                // cancel those jobs
                orphanedJobsForThisAdapter.forEach { workName ->
                    Log.d("BallastWorkManager", "Cancelling orphaned work schedule at '$workName'")
                    workManager.cancelUniqueWork(workName)
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        Result.success()
    }

    public companion object {

        /**
         * Schedule BallastWorkManagerScheduleDispatcher to run immediately as a unique job, replacing any existing
         * jobs. The job is keyed off the [adapter]'s filly-qualified class name.
         *
         * BallastWorkManagerScheduleDispatcher will make sure schedules are configured for all the scheduled registered
         * in the adapter. If a schedule does not exist, it will create it. If a registered schedule is already part of
         * WorkManager, it will leave it alone, since the schedule job will do the work to enqueue each successive
         * invocation. If there are enqueued jobs at keys which are not in the Adapter's registered schedules, those
         * will be cancelled, as it will be assumed they were configured in a previous release, but removed in the
         * current version.
         */
        public fun <T, I : Any, E : Any, S : Any> scheduleWork(
            workManager: WorkManager,
            adapter: T,
        ) where T : SchedulerAdapter<I, E, S>, T : Function1<I, Unit> {
            Log.d("BallastWorkManager", "Scheduling work dispatch")
            val instant = Clock.System.now()
            val adapterClassName = adapter.javaClassName

            workManager
                .beginUniqueWork(
                    /* uniqueWorkName = */ adapterClassName,
                    /* existingWorkPolicy = */ ExistingWorkPolicy.REPLACE,
                    /* work = */ OneTimeWorkRequestBuilder<BallastWorkManagerScheduleDispatcher>()
                        .setInputData(
                            Data.Builder()
                                .putString(DATA_ADAPTER_CLASS, adapterClassName)
                                .putLong(DATA_INITIAL_INSTANT, instant.toEpochMilliseconds())
                                .putLong(DATA_LATEST_INSTANT, instant.toEpochMilliseconds())
                                .build()
                        )
                        .addTag("ballast")
                        .addTag("dispatcher")
                        .addTag(adapterClassName)
                        .build()
                )
                .enqueue()
        }
    }
}
