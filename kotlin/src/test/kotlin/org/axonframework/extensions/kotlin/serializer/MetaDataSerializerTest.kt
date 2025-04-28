/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.extensions.kotlin.serializer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.axonframework.extensions.kotlin.serialization.AxonSerializersModule
import org.axonframework.extensions.kotlin.serialization.KotlinSerializer
import org.axonframework.extensions.kotlin.serialization.axonSerializersModuleWith
import org.axonframework.messaging.MetaData
import org.axonframework.serialization.Serializer
import org.axonframework.serialization.SimpleSerializedObject
import org.axonframework.serialization.SimpleSerializedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MetaDataSerializerTest {

    private val serializer = KotlinSerializer(Json { serializersModule = AxonSerializersModule })

    @Test
    fun `empty metadata`() {
        val metadata = MetaData.emptyInstance()
        val json = """{"values":{}}"""
        assertEquals(json, serializer.serialize(metadata, String::class.java).data)
        assertEquals(metadata, serializer.deserializeMetaData(json))
    }

    @Test
    fun `metadata with primitive values`() {
        val metadata = MetaData.from(mapOf(
            "stringValue" to "test",
            "intValue" to 42,
            "doubleValue" to 3.14,
            "booleanValue" to true,
            "nullValue" to null
        ))
        val json = """{"values":{"stringValue":"test","intValue":42,"doubleValue":3.14,"booleanValue":true,"nullValue":null}}"""
        assertEquals(json, serializer.serialize(metadata, String::class.java).data)
        assertEquals(metadata, serializer.deserializeMetaData(json))
    }

    @Test
    fun `metadata with collection values`() {
        val metadata = MetaData.from(mapOf(
            "listValue" to listOf("a", "b", "c"),
            "mapValue" to mapOf("key1" to "value1", "key2" to "value2"),
            "nestedListValue" to listOf(listOf(1, 2), listOf(3, 4))
        ))
        val json = """{"values":{"listValue":["a","b","c"],"mapValue":{"key1":"value1","key2":"value2"},"nestedListValue":[[1,2],[3,4]]}}"""
        assertEquals(json, serializer.serialize(metadata, String::class.java).data)
        assertEquals(metadata, serializer.deserializeMetaData(json))
    }

    @Test
    fun `metadata with custom objects`() {
        @Serializable
        data class CustomType(val id: String, val value: Int)

        val customModule = axonSerializersModuleWith {
            polymorphic(Any::class) {
                subclass(CustomType::class)
            }
        }

        val customSerializer = KotlinSerializer(Json { serializersModule = customModule })

        val metadata = MetaData.from(mapOf(
            "customObject" to CustomType("test-id", 123)
        ))

        val json = """{"values":{"customObject":{"type":"org.axonframework.extensions.kotlin.serializer.MetaDataSerializerTest.metadata with custom objects.CustomType","id":"test-id","value":123}}}"""
        assertEquals(json, customSerializer.serialize(metadata, String::class.java).data)

        val deserialized = customSerializer.deserializeMetaData(json)
        assertEquals(1, deserialized.size)

        val customObject = deserialized["customObject"] as CustomType
        assertEquals("test-id", customObject.id)
        assertEquals(123, customObject.value)
    }

    @Test
    fun `metadata with mixed values`() {
        val metadata = MetaData.from(mapOf(
            "string" to "text",
            "number" to 42,
            "list" to listOf(1, 2, 3),
            "nested" to mapOf(
                "inner" to true,
                "items" to listOf("a", "b")
            )
        ))

        val json = """{"values":{"string":"text","number":42,"list":[1,2,3],"nested":{"inner":true,"items":["a","b"]}}}"""
        assertEquals(json, serializer.serialize(metadata, String::class.java).data)
        assertEquals(metadata, serializer.deserializeMetaData(json))
    }

    @Test
    fun `test with method`() {
        val metadata = MetaData.with("key", "value")
        val json = """{"values":{"key":"value"}}"""
        assertEquals(json, serializer.serialize(metadata, String::class.java).data)
        assertEquals(metadata, serializer.deserializeMetaData(json))
    }

    @Test
    fun `test and method`() {
        val metadata = MetaData.with("key1", "value1").and("key2", "value2")
        val json = """{"values":{"key1":"value1","key2":"value2"}}"""
        assertEquals(json, serializer.serialize(metadata, String::class.java).data)
        assertEquals(metadata, serializer.deserializeMetaData(json))
    }

    @Test
    fun `test mergedWith method`() {
        val metadata1 = MetaData.with("key1", "value1")
        val metadata2 = MetaData.with("key2", "value2")
        val merged = metadata1.mergedWith(metadata2)

        val json = """{"values":{"key1":"value1","key2":"value2"}}"""
        assertEquals(json, serializer.serialize(merged, String::class.java).data)
        assertEquals(merged, serializer.deserializeMetaData(json))
    }

    @Test
    fun `test withoutKeys method`() {
        val metadata = MetaData.from(mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3"))
        val filtered = metadata.withoutKeys(setOf("key1", "key3"))

        val json = """{"values":{"key2":"value2"}}"""
        assertEquals(json, serializer.serialize(filtered, String::class.java).data)
        assertEquals(filtered, serializer.deserializeMetaData(json))
    }

    @Test
    fun `test subset method`() {
        val metadata = MetaData.from(mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3"))
        val subset = metadata.subset("key1", "key3", "keyNotExist")

        val json = """{"values":{"key1":"value1","key3":"value3"}}"""
        assertEquals(json, serializer.serialize(subset, String::class.java).data)
        assertEquals(subset, serializer.deserializeMetaData(json))
    }

    private fun Serializer.deserializeMetaData(json: String): MetaData {
        val serializedType = SimpleSerializedType(MetaData::class.java.name, null)
        val serializedObj = SimpleSerializedObject(json, String::class.java, serializedType)
        return deserialize(serializedObj)
    }
}