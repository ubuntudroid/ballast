package com.copperleaf.ballast.scheduler

public fun interface SchedulerAdapter<Inputs : Any, Events : Any, State : Any> {
    public fun SchedulerAdapterScope<Inputs, Events, State>.configureSchedules()
}
