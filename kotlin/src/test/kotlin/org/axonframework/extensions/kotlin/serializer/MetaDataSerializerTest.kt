package org.axonframework.extensions.kotlin.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import org.axonframework.extensions.kotlin.serialization.AxonSerializersModule
import org.axonframework.extensions.kotlin.serialization.KotlinSerializer
import org.axonframework.messaging.MetaData
import org.axonframework.serialization.SerializationException
import org.axonframework.serialization.SimpleSerializedObject
import org.axonframework.serialization.SimpleSerializedType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.*

class MetaDataSerializerTest {

    private val jsonSerializer = KotlinSerializer(
        serialFormat = Json {
            serializersModule = AxonSerializersModule
        }
    )

    @OptIn(ExperimentalSerializationApi::class)
    private val cborSerializer = KotlinSerializer(
        serialFormat = Cbor {
            serializersModule = AxonSerializersModule
        }
    )

    @Test
    fun `should serialize and deserialize empty MetaData`() {
        val emptyMetaData = MetaData.emptyInstance()

        val serialized = jsonSerializer.serialize(emptyMetaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(emptyMetaData, deserialized)
        assertTrue(deserialized!!.isEmpty())
    }

    @Test
    fun `should serialize and deserialize MetaData with String values`() {
        val metaData = MetaData.with("key1", "value1")
            .and("key2", "value2")

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData, deserialized)
        assertEquals("value1", deserialized?.get("key1"))
        assertEquals("value2", deserialized?.get("key2"))
    }

    @Test
    fun `should serialize and deserialize MetaData with numeric values`() {
        val metaData = MetaData.with("int", 42)
            .and("long", 123456789L)
            .and("double", 3.14159)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData.size, deserialized!!.size)
        // Note: numbers might be deserialized as different numeric types
        // but their string representation should match
        assertEquals(metaData["int"].toString(), deserialized.get("int").toString())
        assertEquals(metaData["long"].toString(), deserialized.get("long").toString())
        assertEquals(metaData["double"].toString(), deserialized.get("double").toString())
    }

    @Test
    fun `should serialize and deserialize MetaData with boolean values`() {
        val metaData = MetaData.with("isTrue", true)
            .and("isFalse", false)
            .and("isFalseString", "false")

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData, deserialized)
        assertEquals(true, deserialized?.get("isTrue"))
        assertEquals(false, deserialized?.get("isFalse"))
        assertEquals("false", deserialized?.get("isFalseString"))
    }

    @Test
    fun `should serialize and deserialize MetaData with null values`() {
        val metaData = MetaData.with("nullValue", null)
            .and("nonNullValue", "present")

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData, deserialized)
        assertNull(deserialized?.get("nullValue"))
        assertEquals("present", deserialized?.get("nonNullValue"))
    }

    @Test
    fun `should handle UUID`() {
        val uuid = UUID.randomUUID()
        val metaData = MetaData.with("uuid", uuid)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(uuid, metaData.get("uuid"))
    }

    @Test
    fun `should handle Instant as String representation`() {
        val timestamp = Instant.now()

        val metaData = MetaData.with("timestamp", timestamp)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)!!

        assertEquals(timestamp.toString(), deserialized.get("timestamp"))
    }

    @Test
    fun `should work with mixed value types`() {
        val metaData = MetaData.with("string", "text")
            .and("number", 123)
            .and("boolean", true)
            .and("null", null)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData, deserialized)
    }

    @Test
    fun `should work with CBOR format`() {
        val metaData = MetaData.with("string", "text")
            .and("number", 123)
            .and("boolean", true)

        val serialized = cborSerializer.serialize(metaData, ByteArray::class.java)
        val deserialized = cborSerializer.deserialize<ByteArray, MetaData>(serialized)

        assertEquals(metaData, deserialized)
    }

    @Test
    fun `should handle string that looks like a number or boolean`() {
        val metaData = MetaData.with("numberString", "123")
            .and("booleanString", "true")

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData, deserialized)
    }

    @Test
    fun `should handle nested maps in MetaData`() {
        val nestedMap = mapOf(
            "key1" to "value1",
            "key2" to 123,
            "nested" to mapOf("a" to 1, "b" to 2)
        )

        val metaData = MetaData.with("mapValue", nestedMap)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        val deserializedValue = deserialized!!["mapValue"]
        assertEquals(nestedMap, deserializedValue)
    }

    @Test
    fun `should handle lists in MetaData`() {
        val list = listOf("item1", "item2", "item3")

        val metaData = MetaData.with("listValue", list)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData.size, deserialized!!.size)

        val deserializedValue = deserialized["listValue"] as List<*>
        assertTrue(deserializedValue.contains("item1"))
        assertTrue(deserializedValue.contains("item2"))
        assertTrue(deserializedValue.contains("item3"))
    }

    @Test
    fun `should handle complex nested structures in MetaData`() {
        val complexStructure = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true,
            "null" to null,
            "list" to listOf(1, 2, 3),
            "nestedMap" to mapOf(
                "a" to "valueA",
                "b" to listOf("x", "y", "z"),
                "c" to mapOf("nested" to "deepValue")
            )
        )

        val metaData = MetaData.with("complexValue", complexStructure)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val deserialized = jsonSerializer.deserialize<String, MetaData>(serialized)

        assertEquals(metaData.size, deserialized!!.size)

        val deserializedValue = deserialized["complexValue"] as Map<*, *>
        assertEquals(deserializedValue, complexStructure)
    }

    @Test
    fun `should not escape quotes in complex nested structures in MetaData`() {
        val complexStructure = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true,
            "null" to null,
            "list" to listOf(1, 2, 3),
            "nestedMap" to mapOf(
                "a" to "valueA",
                "b" to listOf("x", "y", "z"),
                "c" to mapOf("nested" to "deepValue")
            )
        )

        val metaData = MetaData.with("complexValue", complexStructure)

        val serialized = jsonSerializer.serialize(metaData, String::class.java)
        val json = """{"complexValue":{"string":"value","number":42,"boolean":true,"null":null,"list":[1,2,3],"nestedMap":{"a":"valueA","b":["x","y","z"],"c":{"nested":"deepValue"}}}}"""
        assertEquals(json, serialized.data);
    }

    @Test
    fun `do not handle custom objects`() {
        data class Person(val name: String, val age: Int)

        val person = Person("John Doe", 30)

        val metaData = MetaData.with("personValue", person)

        assertThrows<SerializationException> {
            jsonSerializer.serialize(metaData, String::class.java)
        }
    }

    @Test
    fun `should throw exception when deserializing malformed JSON`() {
        val serializedType = SimpleSerializedType(MetaData::class.java.name, null)

        val syntaxErrorJson = """{"key": value"""  // missing closing bracket around value
        val syntaxErrorObject = SimpleSerializedObject(syntaxErrorJson, String::class.java, serializedType)

        assertThrows<SerializationException> {
            jsonSerializer.deserialize<String, MetaData>(syntaxErrorObject)
        }
    }

}
