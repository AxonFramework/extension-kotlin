package org.axonframework.extensions.kotlin.messaging

import kotlinx.coroutines.runBlocking
import org.axonframework.messaging.Message
import org.axonframework.messaging.MessageDispatchInterceptor
import java.util.function.BiFunction

/**
 * Interceptor that allows messages to be intercepted and modified before they are dispatched.
 *
 * @param <M> the message type this interceptor can process
 * @author Joel Feinstein
 * @since 0.2.0
*/
interface SuspendingMessageDispatchInterceptor<M : Message<*>> : MessageDispatchInterceptor<M> {
    /**
     * Intercepts a message.
     *
     * @param message a message to be intercepted
     * @return the message to dispatch
     */
    suspend fun intercept(message: M): M

    override fun handle(messages: List<M>): BiFunction<Int, M, M> {
        return BiFunction { _, message -> runBlocking { intercept(message) } }
    }
}