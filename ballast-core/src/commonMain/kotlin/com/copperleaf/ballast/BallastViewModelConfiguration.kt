package com.copperleaf.ballast

import kotlinx.coroutines.CoroutineDispatcher

public interface BallastViewModelConfiguration<Inputs : Any, Events : Any, State : Any> {
    public val initialState: State
    public val inputHandler: InputHandler<Inputs, Events, State>
    public val filter: InputFilter<Inputs, Events, State>?
    public val interceptors: List<BallastInterceptor<Inputs, Events, State>>
    public val inputStrategy: InputStrategy
    public val inputsDispatcher: CoroutineDispatcher
    public val eventsDispatcher: CoroutineDispatcher
    public val sideEffectsDispatcher: CoroutineDispatcher
    public val interceptorDispatcher: CoroutineDispatcher
    public val name: String
}
