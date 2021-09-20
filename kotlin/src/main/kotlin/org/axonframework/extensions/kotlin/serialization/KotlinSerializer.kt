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
    private val json: Json = Json,
) : Serializer {

    private val serializerCache: MutableMap<Class<*>, KSerializer<*>> = mutableMapOf()

    override fun <T> serialize(value: Any?, expectedRepresentation: Class<T>): SerializedObject<T> {
        try {
            val type = ObjectUtils.nullSafeTypeOf(value)

            if (expectedRepresentation.isAssignableFrom(JsonElement::class.java)) {
                return SimpleSerializedObject(
                    (if (value == null) JsonNull else json.encodeToJsonElement(type.serializer(), value)) as T,
                    expectedRepresentation,
                    typeForClass(type)
                )
            }

            // By default, encode to String. This can be converted to other types by the converter
            val stringSerialized: SerializedObject<String> = SimpleSerializedObject(
                (if (value == null) "null" else json.encodeToString(type.serializer(), value)),
                String::class.java,
                typeForClass(type)
            )

            return converter.convert(stringSerialized, expectedRepresentation)
        } catch (ex: SerializationException) {
            throw AxonSerializationException("Cannot serialize type ${value?.javaClass?.name} to representation $expectedRepresentation.", ex)
        }
    }

    override fun <T> canSerializeTo(expectedRepresentation: Class<T>): Boolean =
        expectedRepresentation == JsonElement::class.java ||
                converter.canConvert(String::class.java, expectedRepresentation)

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

            if (serializedObject.contentType.isAssignableFrom(JsonElement::class.java)) {
                return json.decodeFromJsonElement(serializer, serializedObject.data as JsonElement)
            }

            val stringSerialized: SerializedObject<String> = converter.convert(serializedObject, String::class.java)
            return json.decodeFromString(serializer, stringSerialized.data)
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