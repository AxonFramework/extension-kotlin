package org.axonframework.extensions.kotlin.queryhandling.gateway

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.axonframework.extensions.kotlin.messaging.andMetaData
import org.axonframework.extensions.kotlin.messaging.registerDispatchInterceptor
import org.axonframework.extensions.kotlin.messaging.registerResultHandlerInterceptor
import org.axonframework.queryhandling.*
import org.junit.jupiter.api.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultSuspendingQueryGatewayTests {
    private lateinit var queryBus: QueryBus
    private lateinit var gateway: SuspendingQueryGateway

    @BeforeEach
    fun setUp() {
        queryBus = mockk()

        gateway = DefaultSuspendingQueryGateway {
            queryBus = this@DefaultSuspendingQueryGatewayTests.queryBus
        }
    }

    @Test
    fun testQuery(): Unit = runBlocking {
        every { queryBus.query<Any?, String>(any()) } answers {
            CompletableFuture.completedFuture(GenericQueryResponseMessage("handled"))
        }

        assertEquals("handled", gateway.query("criteria"))
    }

    @Test
    fun testQueryReturningNull(): Unit = runBlocking {
        every { queryBus.query<Any?, String?>(any()) } answers {
            CompletableFuture.completedFuture(GenericQueryResponseMessage.asNullableResponseMessage(String::class.java, null))
        }

        assertEquals(null, gateway.query<String?>("criteria"))
    }

    @Test
    fun testQueryWithDispatchInterceptor(): Unit = runBlocking {
        every { queryBus.query<Any?, String>(any()) } answers {
            val queryMessage = firstArg<QueryMessage<*,*>>()
            val response = listOfNotNull(queryMessage.metaData["key1"], queryMessage.metaData["key2"]).joinToString("")
            CompletableFuture.completedFuture(GenericQueryResponseMessage.asResponseMessage(response))
        }

        val registration1 = gateway.registerDispatchInterceptor { it.andMetaData("key1" to "value1") }
        val registration2 = gateway.registerDispatchInterceptor { it.andMetaData("key2" to "value2") }

        assertEquals("value1value2", gateway.query(true))
        registration2.cancel()
        assertEquals("value1", gateway.query(true))
        registration1.cancel()
        assertEquals("", gateway.query(true))
    }

    @Test
    fun testQueryWithResultInterceptorAlterResult(): Unit = runBlocking {
        every { queryBus.query<Any?, String>(any()) } answers {
            CompletableFuture.completedFuture(GenericQueryResponseMessage("handled"))
        }

        gateway.registerResultHandlerInterceptor { _, _ ->
            GenericQueryResponseMessage.asResponseMessage<String>("handled-modified")
        }

        assertEquals("handled-modified", gateway.query("criteria"))
    }

    @Test
    fun testQueryWithDispatchInterceptorThrowingAnException(): Unit = runBlocking {
        gateway.registerDispatchInterceptor { throw RuntimeException() }
        assertThrows<RuntimeException> { gateway.query<String>(true) }
    }

    @Test
    fun testQueryFails(): Unit = runBlocking {
        every { queryBus.query<Any?, String>(any()) } throws RuntimeException()
        assertThrows<RuntimeException> {
            gateway.query<Int>(5)
        }
    }

    @Test
    fun testScatterGather(): Unit = runBlocking {
        every { queryBus.scatterGather<Any?, String>(any(), any(), any()) } answers {
            listOf(
                GenericQueryResponseMessage.asResponseMessage<String>("handled"),
                GenericQueryResponseMessage.asResponseMessage<String>("handled"),
            ).stream()
        }

        assertEquals(listOf("handled", "handled"), gateway.scatterGather<String>("criteria", Duration.ofSeconds(1)).toList())
    }

    @Test
    fun testScatterGatherReturningNull(): Unit = runBlocking {
        every { queryBus.scatterGather<Any?, String>(any(), any(), any()) } answers {
            listOf(
                GenericQueryResponseMessage.asNullableResponseMessage(String::class.java, null),
                GenericQueryResponseMessage.asNullableResponseMessage(String::class.java, null),
            ).stream()
        }

        assertEquals(listOf(null, null), gateway.scatterGather<String?>("criteria", Duration.ofSeconds(1)).toList())
    }

    @Test
    fun testScatterGatherWithDispatchInterceptor(): Unit = runBlocking {
        every { queryBus.scatterGather<Any?, String>(any(), any(), any()) } answers {
            val queryMessage = firstArg<QueryMessage<*,*>>()
            val response = listOfNotNull(queryMessage.metaData["key1"], queryMessage.metaData["key2"]).joinToString("")
            listOf(GenericQueryResponseMessage.asResponseMessage<String>(response)).stream()
        }

        val registration1 = gateway.registerDispatchInterceptor { it.andMetaData("key1" to "value1") }
        val registration2 = gateway.registerDispatchInterceptor { it.andMetaData("key2" to "value2") }

        assertEquals(listOf("value1value2"), gateway.scatterGather<String>(true, Duration.ofSeconds(1)).toList())
        registration2.cancel()
        assertEquals(listOf("value1"), gateway.scatterGather<String>(true, Duration.ofSeconds(1)).toList())
        registration1.cancel()
        assertEquals(listOf(""), gateway.scatterGather<String>(true, Duration.ofSeconds(1)).toList())
    }

    @Test
    fun testScatterGatherWithResultIntercept(): Unit = runBlocking {
        every { queryBus.scatterGather<Any?, String>(any(), any(), any()) } answers {
            listOf(GenericQueryResponseMessage.asResponseMessage<String>("handled")).stream()
        }

        gateway.registerResultHandlerInterceptor { _, _ -> GenericQueryResponseMessage("handled-modified") }
        assertEquals("handled-modified", gateway.scatterGather<String>("criteria", Duration.ofSeconds(1)).first())
    }

    @Test
    fun testQueryWithResultInterceptorModifyResultBasedOnQuery(): Unit = runBlocking {
        every { queryBus.query<Any?, String>(any()) } answers {
            CompletableFuture.completedFuture(GenericQueryResponseMessage("handled"))
        }

        gateway.registerDispatchInterceptor { it.andMetaData(mapOf("block" to (it.payload is Boolean))) }
        gateway.registerResultHandlerInterceptor { query, _ ->
            GenericQueryResponseMessage.asResponseMessage<Boolean>(query.metaData["block"] as Boolean?)
        }

        assertFalse(gateway.query("criteria"))
    }

    @Test
    fun testScatterGatherWithResultInterceptReplacedWithError(): Unit = runBlocking {
        every { queryBus.scatterGather<Any?, Any?>(any(), any(), any()) } answers {
            val queryMessage = firstArg<QueryMessage<*,*>>()
            listOf(GenericQueryResponseMessage.asResponseMessage<Any?>(queryMessage.payload)).stream()
        }

        gateway.registerResultHandlerInterceptor { _, result ->
            result.takeUnless { (it.payload as? String)?.isEmpty() == true }
                ?: GenericQueryResponseMessage.asResultMessage<Any?>(RuntimeException("no empty strings allowed"))
        }

        assertThrows<RuntimeException> {
            gateway.scatterGather<String>("", Duration.ofSeconds(1)).first()
        }

        assertEquals("query1", gateway.scatterGather<String>("query1", Duration.ofSeconds(1)).first())
        assertEquals(4, gateway.scatterGather<Int>(4, Duration.ofSeconds(1)).first())
        assertTrue(gateway.scatterGather<Boolean>(true, Duration.ofSeconds(1)).first())
    }

    @Test
    fun testScatterGatherWithDispatchInterceptorThrowingAnException(): Unit = runBlocking {
        gateway.registerDispatchInterceptor { throw RuntimeException() }
        assertThrows<RuntimeException> {
            gateway.scatterGather<String>("", Duration.ofSeconds(1))
        }
    }

    @Test
    fun testScatterGatherFails(): Unit = runBlocking {
        every { queryBus.scatterGather<Any?, Any?>(any(), any(), any()) } throws RuntimeException()
        assertThrows<RuntimeException> {
            gateway.scatterGather<String>("", Duration.ofSeconds(1))
        }
    }

    @Test
    fun testSubscriptionQuery(): Unit = runBlocking {
        every { queryBus.subscriptionQuery<Any?, Any?, Any?>(any(), any(), any()) } answers {
            mockk {
                every { initialResult() } returns Mono.just(GenericQueryResponseMessage.asResponseMessage("handled"))
                every { updates() } returns Flux.just(GenericSubscriptionQueryUpdateMessage.asUpdateMessage("update"))
            }
        }

        val subscription = gateway.subscriptionQuery<String, String>("criteria")
        assertEquals("handled", subscription.initialResult())
        assertEquals("update", subscription.updates().first())
    }

    @Test
    fun testSubscriptionQueryReturningNull(): Unit = runBlocking {
        every { queryBus.subscriptionQuery<Any?, Any?, Any?>(any(), any(), any()) } answers {
            mockk {
                every { initialResult() } returns Mono.just(GenericQueryResponseMessage.asNullableResponseMessage(Any::class.java, null))
                every { updates() } returns Flux.just(GenericSubscriptionQueryUpdateMessage.asUpdateMessage(null))
            }
        }

        val subscription = gateway.subscriptionQuery<String, String>(0L)
        assertNull(subscription.initialResult())
        assertNull(subscription.updates().first())
    }

    @Test
    fun testSubscriptionQueryWithDispatchInterceptor(): Unit = runBlocking {
        every { queryBus.subscriptionQuery<Any?, Any?, Any?>(any(), any(), any()) } answers {
            val queryMessage = firstArg<QueryMessage<*,*>>()
            val response = listOfNotNull(queryMessage.metaData["key1"], queryMessage.metaData["key2"]).joinToString("")
            mockk {
                every { initialResult() } returns Mono.just(GenericQueryResponseMessage.asResponseMessage(response))
                every { updates() } returns Flux.just(GenericSubscriptionQueryUpdateMessage.asUpdateMessage(response))
            }
        }

        val registration1 = gateway.registerDispatchInterceptor { it.andMetaData("key1" to "value1") }
        val registration2 = gateway.registerDispatchInterceptor { it.andMetaData("key2" to "value2") }

        gateway.subscriptionQuery<String, String>(true).let {
            assertEquals("value1value2", it.initialResult())
            assertEquals("value1value2", it.updates().first())
        }

        registration2.cancel()

        gateway.subscriptionQuery<String, String>(true).let {
            assertEquals("value1", it.initialResult())
            assertEquals("value1", it.updates().first())
        }

        registration1.cancel()

        gateway.subscriptionQuery<String, String>(true).let {
            assertEquals("", it.initialResult())
            assertEquals("", it.updates().first())
        }
    }

    @Test
    fun testSubscriptionQueryWithDispatchInterceptorThrowingAnException(): Unit = runBlocking {
        gateway.registerDispatchInterceptor { throw RuntimeException() }
        assertThrows<RuntimeException> {
            gateway.subscriptionQuery<String, String>(true)
        }
    }

    @Test
    fun testSubscriptionQueryFails(): Unit = runBlocking {
        every { queryBus.subscriptionQuery<Any?, Any?, Any?>(any(), any(), any()) } throws RuntimeException()
        assertThrows<RuntimeException> {
            gateway.subscriptionQuery<String, String>(true)
        }
    }

    @Test
    fun testQueryUpdates(): Unit = runBlocking {
        every { queryBus.subscriptionQuery<Any?, Any?, Any?>(any(), any(), any()) } answers {
            mockk {
                every { initialResult() } returns Mono.just(GenericQueryResponseMessage.asResponseMessage("handled"))
                every { updates() } returns Flux.just(GenericSubscriptionQueryUpdateMessage.asUpdateMessage("update"))
                every { cancel() } returns true
            }
        }

        assertEquals(listOf("update"), gateway.queryUpdates<String>("criteria").toList())
    }
}