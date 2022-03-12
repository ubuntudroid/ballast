package com.copperleaf.ballast.debugger

import com.copperleaf.ballast.BallastInterceptor
import com.copperleaf.ballast.BallastNotification
import com.copperleaf.ballast.Queued
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.time.ExperimentalTime

@ExperimentalTime
public class BallastDebuggerInterceptor<Inputs : Any, Events : Any, State : Any>(
    private val connection: BallastDebuggerClientConnection<*>,
) : BallastInterceptor<Inputs, Events, State> {

    override fun start(
        hostViewModelName: String,
        viewModelScope: CoroutineScope,
        notifications: Flow<BallastNotification<Inputs, Events, State>>,
        sendToQueue: suspend (Queued<Inputs, Events, State>) -> Unit
    ) {
        connection.connectViewModel(
            hostViewModelName = hostViewModelName,
            notifications = notifications,
            sendToQueue = sendToQueue,
        )
    }

    override suspend fun onNotify(notification: BallastNotification<Inputs, Events, State>) {}
}
