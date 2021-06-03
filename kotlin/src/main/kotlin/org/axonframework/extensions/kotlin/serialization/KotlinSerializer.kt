/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.extensions.kotlin.serialization

import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.axonframework.common.ObjectUtils
import org.axonframework.serialization.AnnotationRevisionResolver
import org.axonframework.serialization.ChainingConverter
import org.axonframework.serialization.Converter
import org.axonframework.serialization.RevisionResolver
import org.axonframework.serialization.SerializedObject
import org.axonframework.serialization.SerializedType
import org.axonframework.serialization.Serializer
import org.axonframework.serialization.SimpleSerializedObject
import org.axonframework.serialization.SimpleSerializedType
import org.axonframework.serialization.UnknownSerializedType
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import org.axonframework.serialization.SerializationException as AxonSerializationException

/**
 * Implementation of Axon Serializer that uses a kotlinx.serialization implementation.
 * The serialized format is JSON.
 *
 * The DSL function kotlinSerializer can be used to easily configure the parameters
 * for this serializer.
 *
 * @see kotlinx.serialization.Serializer
 * @see org.axonframework.serialization.Serializer
 * @see kotlinSerializer
 *
 * @since 0.2.0
 * @author Hidde Wieringa
 */
class KotlinSerializer(
    private val revisionResolver: RevisionResolver = AnnotationRevisionResolver(),
    private val converter: Converter = ChainingConverter(),
    private val json: Json
) : Serializer {

    private val serializerCache: MutableMap<Class<*>, KSerializer<*>> = mutableMapOf()

    override fun <T> serialize(value: Any?, expectedRepresentation: Class<T>): SerializedObject<T> {
        try {
            val type = ObjectUtils.nullSafeTypeOf(value)
            return when {
                expectedRepresentation.isAssignableFrom(String::class.java) ->
                    SimpleSerializedObject(
                        (if (value == null) "null" else json.encodeToString(type.serializer(), value)) as T,
                        expectedRepresentation,
                        typeForClass(type)
                    )

                expectedRepresentation.isAssignableFrom(JsonElement::class.java) ->
                    SimpleSerializedObject(
                        (if (value == null) JsonNull else json.encodeToJsonElement(type.serializer(), value)) as T,
                        expectedRepresentation,
                        typeForClass(type)
                    )

                else ->
                    throw AxonSerializationException("Cannot serialize type $type to representation $expectedRepresentation. String and JsonElement are supported.")
            }
        } catch (ex: SerializationException) {
            throw AxonSerializationException("Cannot serialize type ${value?.javaClass?.name} to representation $expectedRepresentation.", ex)
        }
    }

    override fun <T> canSerializeTo(expectedRepresentation: Class<T>): Boolean =
        expectedRepresentation == String::class.java ||
                expectedRepresentation == JsonElement::class.java

    override fun <S, T> deserialize(serializedObject: SerializedObject<S>?): T? {
        try {
            if (serializedObject == null) {
                return null
            }

            if (serializedObject.type == SerializedType.emptyType()) {
                return null
            }

            val foundType = classForType(serializedObject.type)
            if (UnknownSerializedType::class.java.isAssignableFrom(foundType)) {
                return UnknownSerializedType(this, serializedObject) as T
            }

            val serializer: KSerializer<T> = foundType.serializer() as KSerializer<T>
            return when {
                serializedObject.contentType.isAssignableFrom(String::class.java) ->
                    json.decodeFromString(serializer, serializedObject.data as String)

                serializedObject.contentType.isAssignableFrom(JsonElement::class.java) ->
                    json.decodeFromJsonElement(serializer, serializedObject.data as JsonElement)

                else ->
                    throw AxonSerializationException("Cannot deserialize from content type ${serializedObject.contentType} to type ${serializedObject.type}. String and JsonElement are supported.")
            }
        } catch (ex: SerializationException) {
            throw AxonSerializationException(
                "Could not deserialize from content type ${serializedObject?.contentType} to type ${serializedObject?.type}",
                ex
            )
        }
    }

    private fun <S, T> SerializedObject<S>.serializer(): KSerializer<T> =
        classForType(type).serializer() as KSerializer<T>

    /**
     * When a type is compiled by the Kotlin compiler extension, a companion object
     * is created which contains a method `serializer()`. This method should be called
     * to get the serializer of the class.
     *
     * In a 'normal' serialization environment, you would call the MyClass.serializer()
     * method directly. Here we are in a generic setting, and need reflection to call
     * the method.
     *
     * If there is no `serializer` method, a ContextualSerializer will be created. This
     * serializer requires manual configuration of the SerializersModule containing a
     * KSerializer which will be used when this class is serialized.
     *
     * This method caches the reflection mapping from class to serializer for efficiency.
     */
    private fun <T> Class<T>.serializer(): KSerializer<T> =
        serializerCache.computeIfAbsent(this) {
            // Class<T>: T must be non-null
            val kClass = (this as Class<Any>).kotlin

            val companion = kClass.companionObject
                ?: return@computeIfAbsent ContextualSerializer(kClass)

            val serializerMethod = companion.java.getMethod("serializer")
                ?: return@computeIfAbsent ContextualSerializer(kClass)

            serializerMethod.invoke(kClass.companionObjectInstance) as KSerializer<*>
        } as KSerializer<T>

    override fun classForType(type: SerializedType): Class<*> =
        if (SerializedType.emptyType() == type) {
            Void.TYPE
        } else {
            try {
                Class.forName(type.name)
            } catch (e: ClassNotFoundException) {
                UnknownSerializedType::class.java
            }
        }

    override fun typeForClass(type: Class<*>?): SerializedType =
        if (type == null || Void.TYPE == type || Void::class.java == type) {
            SimpleSerializedType.emptyType()
        } else {
            SimpleSerializedType(type.name, revisionResolver.revisionOf(type))
        }

    override fun getConverter(): Converter =
        converter

}

/**
 * Configuration which will be used to construct a KotlinSerializer.
 * This class is used in the kotlinSerializer DSL function.
 *
 * @see KotlinSerializer
 * @see kotlinSerializer
 */
class KotlinSerializerConfiguration {
    var revisionResolver: RevisionResolver = AnnotationRevisionResolver()
    var converter: Converter = ChainingConverter()
    var json: Json = Json
}

/**
 * DSL function to configure a new KotlinSerializer.
 *
 * Usage example:
 * <code>
 *     val serializer: KotlinSerializer = kotlinSerializer {
 *          json = Json
 *          converter = ChainingConverter()
 *          revisionResolver = AnnotationRevisionResolver()
 *     }
 * </code>
 *
 * @see KotlinSerializer
 */
fun kotlinSerializer(init: KotlinSerializerConfiguration.() -> Unit = {}): KotlinSerializer {
    val configuration = KotlinSerializerConfiguration()
    configuration.init()
    return KotlinSerializer(
        configuration.revisionResolver,
        configuration.converter,
        configuration.json,
    )
}