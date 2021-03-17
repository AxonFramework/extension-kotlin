package org.axonframework.extensions.kotlin.eventhandling.gateway

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.axonframework.eventhandling.EventBus
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.extensions.kotlin.messaging.andMetaData
import org.axonframework.extensions.kotlin.messaging.registerDispatchInterceptor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class DefaultSuspendingEventGatewayTests {
    private lateinit var eventBus: EventBus
    private lateinit var gateway: SuspendingEventGateway

    @BeforeEach
    fun setUp() {
        eventBus = mockk {
            justRun { publish(any<EventMessage<*>>()) }
        }

        gateway = DefaultSuspendingEventGateway {
            eventBus = this@DefaultSuspendingEventGatewayTests.eventBus
        }
    }

    @Test
    fun testPublish(): Unit = runBlocking {
        gateway.publish("event", "event")
        verify(exactly = 2) { eventBus.publish(any<EventMessage<*>>()) }
    }

    @Test
    fun testPublishWithError(): Unit = runBlocking {
        every { eventBus.publish(any<EventMessage<*>>()) } throws RuntimeException("oops")
        assertThrows<RuntimeException> {
            gateway.publish("event", "event")
        }
    }

    @Test
    fun testDispatchInterceptor(): Unit = runBlocking {
        gateway.registerDispatchInterceptor {
            GenericEventMessage.asEventMessage<Any?>(it.payload).andMetaData("key" to "value")
        }
        assertEquals("value", gateway.publish("event").first().metaData["key"])
    }

    @Test
    fun testPublishOrder(): Unit = runBlocking {
        val event1 = GenericEventMessage.asEventMessage<Any>("event1")
        val event2 = GenericEventMessage.asEventMessage<Any>("event2")
        assertEquals(listOf(event1, event2), gateway.publish(event1, event2))
    }
}