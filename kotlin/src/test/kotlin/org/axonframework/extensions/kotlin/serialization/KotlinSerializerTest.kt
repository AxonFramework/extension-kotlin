package org.axonframework.extensions.kotlin.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.axonframework.eventhandling.tokenstore.ConfigToken
import org.axonframework.extensions.kotlin.serialization.serializer.ConfigTokenSerializer
import org.axonframework.serialization.AnnotationRevisionResolver
import org.axonframework.serialization.ChainingConverter
import org.axonframework.serialization.SerializedType
import org.axonframework.serialization.SimpleSerializedObject
import org.axonframework.serialization.UnknownSerializedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KotlinSerializerTest {

    /**
     * This class will automatically become serializable through the Kotlin serialization compiler plugin.
     */
    @Serializable
    data class TestData(
        val name: String,
        val value: Float?
    )

    @Test
    fun canSerializeTo() {
        val serializer = kotlinSerializer()

        assertTrue(serializer.canSerializeTo(String::class.java))
        assertTrue(serializer.canSerializeTo(JsonElement::class.java))
    }

    @Test
    fun `configuration options`() {
        val serializer = kotlinSerializer {
            json = Json
            converter = ChainingConverter()
            revisionResolver = AnnotationRevisionResolver()
        }
        assertNotNull(serializer)
    }

    @Test
    fun serialize() {
        val serializer = kotlinSerializer()

        val emptySerialized = serializer.serialize(TestData("", null), String::class.java)
        assertEquals("SimpleSerializedType[org.axonframework.extensions.kotlin.serialization.KotlinSerializerTest\$TestData] (revision null)", emptySerialized.type.toString())
        assertEquals("""{"name":"","value":null}""", emptySerialized.data)
        assertEquals(String::class.java, emptySerialized.contentType)

        val filledSerialized = serializer.serialize(TestData("name", 1.23f), String::class.java)
        assertEquals("SimpleSerializedType[org.axonframework.extensions.kotlin.serialization.KotlinSerializerTest\$TestData] (revision null)", filledSerialized.type.toString())
        assertEquals("""{"name":"name","value":1.23}""", filledSerialized.data)
        assertEquals(String::class.java, filledSerialized.contentType)

        val nullSerialized = serializer.serialize(null, String::class.java)
        assertEquals("null", nullSerialized.data)
        assertEquals(String::class.java, nullSerialized.contentType)
    }

    @Test
    fun deserialize() {
        val serializer = kotlinSerializer()

        val nullDeserialized: Any? = serializer.deserialize(
            SimpleSerializedObject(
                "",
                String::class.java,
                SerializedType.emptyType()
            )
        )
        assertNull(nullDeserialized)

        val emptyDeserialized: Any? = serializer.deserialize(
            SimpleSerializedObject(
                """{"name":"","value":null}""",
                String::class.java,
                TestData::class.java.name,
                null
            )
        )
        assertNotNull(emptyDeserialized as TestData)
        assertEquals(emptyDeserialized.name, "")
        assertEquals(emptyDeserialized.value, null)

        val filledDeserialized: Any? = serializer.deserialize(
            SimpleSerializedObject(
                """{"name":"name","value":1.23}""",
                String::class.java,
                TestData::class.java.name,
                null
            )
        )
        assertNotNull(filledDeserialized as TestData)
        assertEquals(filledDeserialized.name, "name")
        assertEquals(filledDeserialized.value, 1.23f)

        val unknownDeserializedType: Any? = serializer.deserialize(
            SimpleSerializedObject(
                """anything""",
                String::class.java,
                UnknownSerializedType::class.java.name,
                null
            )
        )
        assertNotNull(unknownDeserializedType as UnknownSerializedType)
    }

    @Test
    fun `byte arrays`() {
        val serializer = kotlinSerializer()

        assertNotNull(serializer.deserialize(serializer.serialize(TestData("name", null), ByteArray::class.java)))
    }

    @Test
    fun `example of custom serializer for ConfigToken`() {
        val serializer = kotlinSerializer {
            json = Json {
                serializersModule = SerializersModule {
                    contextual(ConfigTokenSerializer())
                }
            }
        }

        val tokenBefore = ConfigToken(mapOf("test" to "value"))
        val serialized = serializer.serialize(tokenBefore, String::class.java)
        assertEquals("""{"config":{"test":"value"}}""", serialized.data)
        val token: ConfigToken? = serializer.deserialize(serialized)
        assertNotNull(token as ConfigToken)
        assertEquals("value", token.get("test"))
    }
}