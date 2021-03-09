package org.axonframework.extensions.kotlin.messaging

import kotlinx.coroutines.flow.Flow
import org.axonframework.messaging.Message
import org.axonframework.messaging.ResultMessage

/**
 * Interceptor that allows results to be intercepted and modified before they are handled. Implementations are required
 * to operate on a [Flow] of results or return a new [Flow] which will be passed down the interceptor chain.
 * Also, implementations may make decisions based on the message that was dispatched.
 *
 * @param <M> The type of the message for which the result is going to be intercepted
 * @param <R> The type of the result to be intercepted
 * @author Joel Feinstein
 * @since 0.2.0
</R></M> */
interface SuspendingResultHandlerInterceptor<M : Message<*>, R : ResultMessage<*>> {
    /**
     * Intercepts result messages.
     *
     * @param message a message that was dispatched (and caused these `results`)
     * @param results the outcome of the dispatched `message`
     * @return the intercepted `results`
     */
    suspend fun intercept(message: M, result: R): R
}