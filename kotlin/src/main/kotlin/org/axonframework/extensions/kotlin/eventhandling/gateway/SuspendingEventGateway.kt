package org.axonframework.extensions.kotlin.eventhandling.gateway

import org.axonframework.eventhandling.EventMessage
import org.axonframework.extensions.kotlin.messaging.SuspendingMessageDispatchInterceptor
import org.axonframework.extensions.kotlin.messaging.SuspendingMessageDispatchInterceptorSupport
import java.util.*

/**
 * Variation of the [EventGateway], wrapping a [EventBus] for a friendlier API.
 * Provides support for Kotlin coroutines.
 *
 * @author Joel Feinstein
 * @since 0.2.0
 */
interface SuspendingEventGateway : SuspendingMessageDispatchInterceptorSupport<EventMessage<*>> {
    /**
     * Publishes given `events`.
     *
     * Given `events` are wrapped as payloads of a [EventMessage] that are eventually published on the
     * [EventBus], unless `event` already implements [Message]. In that case, a `EventMessage`
     * is constructed from that message's payload and [org.axonframework.messaging.MetaData].
     *
     * @param events events to be published
     * @return events that were published. DO NOTE: if there were some [SuspendingMessageDispatchInterceptor]s
     * registered to this `gateway`, they will be processed first, before returning events to the caller. The
     * order of returned events is the same as one provided as the input parameter.
     */
    suspend fun publish(vararg events: Any?): List<EventMessage<*>> {
        return publish(events.toList())
    }

    /**
     * Publishes the given `events`.
     *
     * Given `events` are wrapped as payloads of a [EventMessage] that are eventually published on the
     * [EventBus], unless `event` already implements [Message]. In that case, a `EventMessage`
     * is constructed from that message's payload and [org.axonframework.messaging.MetaData].
     *
     * @param events the list of events to be published
     * @return events that were published. DO NOTE: if there were some [SuspendingMessageDispatchInterceptor]s
     * registered to this `gateway`, they will be processed first, before returning events to the caller. The
     * order of returned events is the same as one provided as the input parameter.
     */
    suspend fun publish(events: Iterable<*>): List<EventMessage<*>>
}