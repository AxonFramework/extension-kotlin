package org.axonframework.extensions.kotlin.commandhandling.gateway

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.axonframework.commandhandling.*
import org.axonframework.extensions.kotlin.messaging.registerDispatchInterceptor
import org.axonframework.extensions.kotlin.messaging.registerResultHandlerInterceptor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class DefaultSuspendingCommandGatewayTests {
    private lateinit var commandBus: CommandBus
    private lateinit var gateway: SuspendingCommandGateway

    @BeforeEach
    fun setUp() {
        commandBus = mockk {
            every { dispatch<Any?, Any?>(any(), any()) } answers {
                val commandMessage: CommandMessage<Any?> = firstArg()
                val commandCallback: CommandCallback<Any?, Any?> = lastArg()
                commandCallback.onResult(commandMessage, GenericCommandResultMessage(commandMessage.payload))
            }
        }

        gateway = DefaultSuspendingCommandGateway {
            commandBus = this@DefaultSuspendingCommandGatewayTests.commandBus
        }
    }

    @Test
    fun testInterceptorOrder(): Unit = runBlocking {
        val metadata1 = mapOf("k1" to "v1")
        gateway.registerDispatchInterceptor { it.andMetaData(metadata1) }
        gateway.registerResultHandlerInterceptor { command, _ -> GenericCommandResultMessage(command.metaData["k1"]) }

        val metadata2 = mapOf("k1" to "v2")
        gateway.registerDispatchInterceptor { it.andMetaData(metadata2) }

        gateway.send<String>("")

        slot<CommandMessage<*>>().apply {
            verify { commandBus.dispatch(capture(this@apply), any<CommandCallback<Any?, Any?>>()) }
            assertEquals("v2", captured.metaData["k1"])
        }
    }

    @Test
    fun testResultFiltering(): Unit = runBlocking {
        gateway.registerResultHandlerInterceptor { _, result -> GenericCommandResultMessage("K" in result.metaData) }
        assertEquals(false, gateway.send(""))
    }

    @Test
    fun testCommandFiltering(): Unit = runBlocking {
        gateway.registerDispatchInterceptor { GenericCommandMessage("K" in it.metaData) }
        assertEquals(false, gateway.send(""))
    }

    @Test
    fun testCommandDispatchAndResultHandlerInterceptor(): Unit = runBlocking {
        val principalMetadata = mapOf("username" to "admin")
        gateway.registerDispatchInterceptor { it.andMetaData(principalMetadata) }
        gateway.registerDispatchInterceptor { it.also { assertEquals("admin", it.metaData["username"]) } }
        gateway.registerResultHandlerInterceptor { _, result -> result.andMetaData(principalMetadata) }
        gateway.registerResultHandlerInterceptor { _, result -> result.also { assertEquals("admin", result.metaData["username"]) } }
        gateway.send<Unit>("")
    }

    @Test
    fun testCommandResultHandlerChain(): Unit = runBlocking {
        val metadata1 = mapOf("k1" to "v1")
        gateway.registerResultHandlerInterceptor { _, result -> result.andMetaData(metadata1) }

        val metadata2 = mapOf("k1" to "v2")
        gateway.registerResultHandlerInterceptor { _, result -> result.andMetaData(metadata2) }

        val metadata3 = mapOf("k2" to "v3")
        gateway.registerResultHandlerInterceptor { _, result -> result.andMetaData(metadata3) }

        gateway.registerResultHandlerInterceptor { _, result ->
            result.also {
                assertEquals("v2", result.metaData["k1"])
                assertEquals("v3", result.metaData["k2"])
            }
        }

        gateway.send<Unit>("")
    }

    @Test
    fun testResultErrorMapping(): Unit = runBlocking {
        every { commandBus.dispatch<Any?, Any?>(any(), any()) } answers {
            val commandMessage: CommandMessage<Any?> = firstArg()
            val commandCallback: CommandCallback<Any?, Any?> = lastArg()
            commandCallback.onResult(commandMessage, GenericCommandResultMessage(RuntimeException("oops")))
        }

        gateway.registerResultHandlerInterceptor { _, result -> GenericCommandResultMessage(result.exceptionResult().message) }

        assertEquals("oops",  gateway.send(""))
    }

    @Test
    fun testResultErrorThrowing(): Unit = runBlocking {
        every { commandBus.dispatch<Any?, Any?>(any(), any()) } answers {
            val commandMessage: CommandMessage<Any?> = firstArg()
            val commandCallback: CommandCallback<Any?, Any?> = lastArg()
            commandCallback.onResult(commandMessage, GenericCommandResultMessage(RuntimeException("oops")))
        }

        assertThrows<RuntimeException> { gateway.send("") }
    }

    @Test
    fun testCommandMessageAlteration(): Unit = runBlocking {
        gateway.registerResultHandlerInterceptor { command, result ->
            if ("kX" in command.metaData) {
                GenericCommandResultMessage("new Payload")
            } else {
                result
            }
        }

        val commandMetadata = mapOf("kX" to "vX")
        assertEquals("", gateway.send(GenericCommandMessage("")))
        assertEquals("new Payload", gateway.send(GenericCommandMessage("").andMetaData(commandMetadata)))
    }

    @Test
    fun testRegisterSuspendingInterceptors(): Unit = runBlocking {
        suspend fun <T> T.identity() = this
        gateway.registerDispatchInterceptor { it.identity() }
        gateway.registerResultHandlerInterceptor { _, result -> result.identity() }
    }
}