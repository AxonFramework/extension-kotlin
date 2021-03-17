package org.axonframework.extensions.kotlin.messaging

import org.axonframework.common.Registration
import org.axonframework.messaging.Message

/**
 * Interface marking components capable of registering a {@link SuspendingMessageDispatchInterceptor}.
 * Generally, these are messaging components injected into the sending end of the communication.
 *
 * @param <M> The type of the message to be intercepted
 * @author Joel Feinstein
 * @since 0.2.0
 */
fun interface SuspendingMessageDispatchInterceptorSupport<M : Message<*>> {
    /**
     * Register the given [SuspendingMessageDispatchInterceptor]. After registration, the interceptor will be
     * invoked for each message dispatched on the messaging component that it was registered to.
     *
     * @param interceptor The reactive interceptor to register
     * @return a [Registration], which may be used to unregister the interceptor
     */
    fun registerDispatchInterceptor(interceptor: SuspendingMessageDispatchInterceptor<M>): Registration
}

fun <M: Message<*>> SuspendingMessageDispatchInterceptorSupport<M>.registerDispatchInterceptor(
    interceptor: suspend (M) -> M
) = registerDispatchInterceptor(
    object : SuspendingMessageDispatchInterceptor<M> {
        override suspend fun intercept(message: M) = interceptor(message)
    }
)
