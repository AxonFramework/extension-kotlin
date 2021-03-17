package org.axonframework.extensions.kotlin.queryhandling.gateway

import kotlinx.coroutines.flow.Flow
import org.axonframework.common.Registration

interface SuspendingSubscriptionQueryResult<I, U> : Registration {
    suspend fun initialResult(): I

    fun updates(): Flow<U>
}
