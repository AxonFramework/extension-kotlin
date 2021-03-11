package org.axonframework.extensions.kotlin.eventhandling.gateway

import org.axonframework.common.BuilderUtils
import org.axonframework.common.Registration
import org.axonframework.eventhandling.EventBus
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.extensions.kotlin.messaging.SuspendingMessageDispatchInterceptor
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Implementation of the [SuspendingEventGateway].
 *
 * @author Joel Feinstein
 * @since 0.2.0
 */
class DefaultSuspendingEventGateway(config: Builder.() -> Unit) : SuspendingEventGateway {
    private val eventBus: EventBus
    private val dispatchInterceptors: MutableList<SuspendingMessageDispatchInterceptor<EventMessage<*>>>

    init {
        Builder().apply(config).validate().let {
            eventBus = it.eventBus
            dispatchInterceptors = CopyOnWriteArrayList(it.dispatchInterceptors)
        }
    }

    /**
     * This implementation will process interceptors and dispatch each event before moving on to the next.
     */
    override suspend fun publish(events: Iterable<*>): List<EventMessage<*>> {
        return events.map {
            GenericEventMessage.asEventMessage<Any>(it).processEventInterceptors().apply { publish() }
        }
    }

    override fun registerDispatchInterceptor(interceptor: SuspendingMessageDispatchInterceptor<EventMessage<*>>): Registration {
        dispatchInterceptors += interceptor
        return Registration { dispatchInterceptors.remove(interceptor) }
    }

    private suspend fun EventMessage<*>.processEventInterceptors(): EventMessage<*> {
        return dispatchInterceptors.fold(this) { acc, interceptor -> interceptor.intercept(acc) }
    }

    private fun EventMessage<*>.publish() {
        eventBus.publish(this)
    }

    /**
     * Builder class to instantiate [DefaultSuspendingEventGateway].
     *
     * The `dispatchInterceptors` are defaulted to an empty list.
     * The [EventBus] is a **hard requirement**
     *
     */
    class Builder {
        lateinit var eventBus: EventBus
        val dispatchInterceptors: List<SuspendingMessageDispatchInterceptor<EventMessage<*>>> = CopyOnWriteArrayList()

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         * specifications
         */
        fun validate() = apply {
            BuilderUtils.assertThat(this::eventBus.isInitialized, { it == true }, "The EventBus is a hard requirement and should be provided")
        }
    }
}
