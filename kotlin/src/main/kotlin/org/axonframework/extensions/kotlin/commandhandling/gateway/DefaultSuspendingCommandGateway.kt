package org.axonframework.extensions.kotlin.commandhandling.gateway

import kotlinx.coroutines.suspendCancellableCoroutine
import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.CommandCallback
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.commandhandling.CommandResultMessage
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.commandhandling.gateway.RetryScheduler
import org.axonframework.commandhandling.gateway.RetryingCallback
import org.axonframework.common.BuilderUtils
import org.axonframework.common.Registration
import org.axonframework.extensions.kotlin.messaging.payloadOrThrowException
import org.axonframework.extensions.kotlin.messaging.SuspendingMessageDispatchInterceptor
import org.axonframework.extensions.kotlin.messaging.SuspendingResultHandlerInterceptor
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class DefaultSuspendingCommandGateway(config: Builder.() -> Unit) : SuspendingCommandGateway {
    private val commandBus: CommandBus
    private val retryScheduler: RetryScheduler?
    private val dispatchInterceptors: MutableList<SuspendingMessageDispatchInterceptor<CommandMessage<*>>>
    private val resultInterceptors: MutableList<SuspendingResultHandlerInterceptor<CommandMessage<*>, CommandResultMessage<*>>>

    init {
        Builder().apply(config).validate().let {
            commandBus = it.commandBus
            retryScheduler = it.retrySchedule
            dispatchInterceptors = CopyOnWriteArrayList(it.dispatchInterceptors)
            resultInterceptors = CopyOnWriteArrayList(it.resultInterceptors)
        }
    }

    override suspend fun <R> send(command: Any?): R {
        val commandMessage = GenericCommandMessage.asCommandMessage<Any?>(command).processDispatchInterceptors()
        return processResultInterceptors(commandMessage, commandMessage.dispatch()).payloadOrThrowException()
    }

    override fun registerDispatchInterceptor(interceptor: SuspendingMessageDispatchInterceptor<CommandMessage<*>>): Registration {
        dispatchInterceptors += interceptor
        return Registration { dispatchInterceptors.remove(interceptor) }
    }

    override fun registerResultHandlerInterceptor(interceptor: SuspendingResultHandlerInterceptor<CommandMessage<*>, CommandResultMessage<*>>): Registration {
        resultInterceptors += interceptor
        return Registration { resultInterceptors.remove(interceptor) }
    }

    private suspend fun CommandMessage<*>.processDispatchInterceptors(): CommandMessage<*> {
        return dispatchInterceptors.fold(this) { acc, interceptor -> interceptor.intercept(acc) }
    }

    private suspend fun processResultInterceptors(commandMessage: CommandMessage<*>, commandResultMessage: CommandResultMessage<*>): CommandResultMessage<*> {
        return resultInterceptors.fold(commandResultMessage) { acc, interceptor -> interceptor.intercept(commandMessage, acc) }
    }

    private suspend fun CommandMessage<*>.dispatch(): CommandResultMessage<*> {
        return suspendCancellableCoroutine { continuation ->
            var callback: CommandCallback<Any?, Any?> = CommandCallback<Any?, Any?> { _, commandResultMessage ->
                continuation.resume(commandResultMessage)
            }

            if (retryScheduler != null) {
                callback = RetryingCallback(callback, retryScheduler, commandBus)
            }

            commandBus.dispatch(this, callback)
        }
    }

    class Builder internal constructor() {
        lateinit var commandBus: CommandBus
        var retrySchedule: RetryScheduler? = null
        val dispatchInterceptors: MutableList<SuspendingMessageDispatchInterceptor<CommandMessage<*>>> = CopyOnWriteArrayList()
        val resultInterceptors: MutableList<SuspendingResultHandlerInterceptor<CommandMessage<*>, CommandResultMessage<*>>> = CopyOnWriteArrayList()

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         * specifications
         */
        fun validate() = apply {
            BuilderUtils.assertThat(this::commandBus.isInitialized, { it == true }, "The CommandBus is a hard requirement and should be provided")
        }
    }
}
