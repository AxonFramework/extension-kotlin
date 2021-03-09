package org.axonframework.extensions.kotlin.messaging

import org.axonframework.common.Registration
import org.axonframework.messaging.Message
import org.axonframework.messaging.ResultMessage

/**
 * Interface marking components capable of registering a [SuspendingResultHandlerInterceptor].
 * Generally, these are messaging components injected into the receiving end of the communication.
 *
 * @param <M> The type of the message for which the result is going to be intercepted
 * @param <R> The type of the result to be intercepted
 * @author Joel Feinstein
 * @since 0.2.0
 */
fun interface SuspendingResultHandlerInterceptorSupport<M : Message<*>, R : ResultMessage<*>> {
    /**
     * Register the given [SuspendingResultHandlerInterceptor]. After registration, the interceptor will be invoked
     * for each result message received on the messaging component that it was registered to.
     *
     * @param interceptor The reactive interceptor to register
     * @return a [Registration], which may be used to unregister the interceptor
     */
    fun registerResultHandlerInterceptor(interceptor: SuspendingResultHandlerInterceptor<M, R>): Registration
}

fun <M: Message<*>, R: ResultMessage<*>> SuspendingResultHandlerInterceptorSupport<M, R>.registerResultHandlerInterceptor(
    interceptor: suspend (M, R) -> R
) = registerResultHandlerInterceptor(
    object : SuspendingResultHandlerInterceptor<M, R> {
        override suspend fun intercept(message: M, result: R) = interceptor(message, result)
    }
)
