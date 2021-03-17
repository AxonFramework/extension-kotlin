package org.axonframework.extensions.kotlin.commandhandling.gateway

import org.axonframework.commandhandling.CommandMessage
import org.axonframework.commandhandling.CommandResultMessage
import org.axonframework.extensions.kotlin.messaging.SuspendingMessageDispatchInterceptorSupport
import org.axonframework.extensions.kotlin.messaging.SuspendingResultHandlerInterceptorSupport

/**
 * Variation of the [CommandGateway], wrapping a [CommandBus] for a friendlier API.
 * Provides support for Kotlin coroutines.
 *
 * @author Joel Feinstein
 * @since 0.2.0
 */
interface SuspendingCommandGateway :SuspendingMessageDispatchInterceptorSupport<CommandMessage<*>>,
    SuspendingResultHandlerInterceptorSupport<CommandMessage<*>, CommandResultMessage<*>> {

    /**
     * Sends the given {@code command} and returns the result.
     * <p/>
     * The given {@code command} is wrapped as the payload of a {@link CommandMessage} that is eventually posted on the
     * {@link CommandBus}, unless the {@code command} already implements {@link Message}. In that case, a
     * {@code CommandMessage} is constructed from that message's payload and {@link MetaData}.
     *
     * @param command the command to dispatch
     * @param <R>     the type of the command result
     * @return a {@link Mono} which is resolved when the command is executed
     */
    suspend fun <R> send(command: Any?): R
}
