package com.copperleaf.ballast.examples.scheduler

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import androidx.work.WorkManager
import androidx.work.WorkManagerInitializer
import com.copperleaf.ballast.scheduler.workmanager.BallastWorkManagerScheduleDispatcher

public class AndroidSchedulerStartup : Initializer<Unit> {
    override fun create(context: Context) {
        Log.d("BallastWorkManager", "Running AndroidSchedulerStartup")
        val workManager = WorkManager.getInstance(context)

        BallastWorkManagerScheduleDispatcher.scheduleWork(
            workManager,
            AndroidSchedulerExampleAdapter()
        )
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(WorkManagerInitializer::class.java)
    }
}
