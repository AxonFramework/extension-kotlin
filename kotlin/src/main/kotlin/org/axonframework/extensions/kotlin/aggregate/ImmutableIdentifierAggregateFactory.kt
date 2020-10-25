/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.extensions.kotlin.aggregate

import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventsourcing.AggregateFactory
import org.axonframework.extensions.kotlin.aggregate.AggregateIdentifierConverter.DefaultString
import org.axonframework.extensions.kotlin.aggregate.AggregateIdentifierConverter.DefaultUUID
import org.axonframework.extensions.kotlin.invokeReporting
import java.util.*
import kotlin.reflect.KClass

/**
 * Factory to create aggregates [A] with immutable aggregate identifier of type [ID].
 * @constructor creates aggregate factory.
 * @param clazz aggregate class.
 * @param idClazz aggregate identifier class.
 * @param aggregateFactoryMethod factory method to create instances, defaults to default constructor of the provided [clazz].
 * @param idExtractor function to convert aggregate identifier from string to [ID].
 * @param [A] aggregate type.
 * @param [ID] aggregate identifier type.
 *
 * @since 0.2.0
 * @author Simon Zambrovski
 */
data class ImmutableIdentifierAggregateFactory<A : Any, ID : Any>(
        val clazz: KClass<A>,
        val idClazz: KClass<ID>,
        val aggregateFactoryMethod: AggregateFactoryMethod<ID, A> = extractConstructorFactory(clazz, idClazz),
        val idExtractor: AggregateIdentifierConverter<ID>
) : AggregateFactory<A> {

    companion object {

        /**
         * Reified factory method for aggregate factory using string as aggregate identifier.
         * @return instance of ImmutableIdentifierAggregateFactory
         */
        inline fun <reified A : Any> usingStringIdentifier() = usingIdentifier<A, String>(String::class) { it }

        /**
         * Factory method for aggregate factory using string as aggregate identifier.
         * @return instance of ImmutableIdentifierAggregateFactory
         */
        fun <A : Any> usingStringIdentifier(clazz: KClass<A>) = usingIdentifier(aggregateClazz = clazz, idClazz = String::class, idExtractor = DefaultString)

        /**
         * Reified factory method for aggregate factory using UUID as aggregate identifier.
         * @return instance of ImmutableIdentifierAggregateFactory
         */
        inline fun <reified A : Any> usingUUIDIdentifier() = usingIdentifier<A, UUID>(idClazz = UUID::class, idExtractor = DefaultUUID::apply)

        /**
         * Factory method for aggregate factory using UUID as aggregate identifier.
         * @return instance of ImmutableIdentifierAggregateFactory
         */
        fun <A : Any> usingUUIDIdentifier(clazz: KClass<A>) = usingIdentifier(aggregateClazz = clazz, idClazz = UUID::class, idExtractor = DefaultUUID)

        /**
         * Reified factory method for aggregate factory using specified identifier type and converter function.
         * @param idClazz identifier class.
         * @param idExtractor extractor function for identifier from string.
         * @return instance of ImmutableIdentifierAggregateFactory
         */
        inline fun <reified A : Any, ID : Any> usingIdentifier(idClazz: KClass<ID>, noinline idExtractor: (String) -> ID) =
                ImmutableIdentifierAggregateFactory(clazz = A::class, idClazz = idClazz, idExtractor = object : AggregateIdentifierConverter<ID> {
                    override fun apply(it: String): ID = idExtractor(it)
                })

        /**
         * Factory method for aggregate factory using specified identifier type and converter.
         * @param idClazz identifier class.
         * @param idExtractor extractor for identifier from string.
         * @return instance of ImmutableIdentifierAggregateFactory
         */
        fun <A : Any, ID : Any> usingIdentifier(aggregateClazz: KClass<A>, idClazz: KClass<ID>, idExtractor: AggregateIdentifierConverter<ID>) =
                ImmutableIdentifierAggregateFactory(clazz = aggregateClazz, idClazz = idClazz, idExtractor = idExtractor)

        /**
         * Tries to extract constructor from given class. Used as a default factory method for the aggregate.
         * @param clazz aggregate class.
         * @param idClazz id class.
         * @return factory method to create new instances of aggregate.
         */
        fun <ID : Any, A : Any> extractConstructorFactory(clazz: KClass<A>, idClazz: KClass<ID>): AggregateFactoryMethod<ID, A> = {
            val constructor = invokeReporting(
                    "The aggregate [${clazz.java.name}] doesn't provide a constructor for the identifier type [${idClazz.java.name}]."
            ) { clazz.java.getConstructor(idClazz.java) }
            constructor.newInstance(it)
        }
    }


    @Throws(IllegalArgumentException::class)
    override fun createAggregateRoot(aggregateIdentifier: String, message: DomainEventMessage<*>?): A {

        val id: ID = invokeReporting(
                "The identifier [$aggregateIdentifier] could not be converted to the type [${idClazz.java.name}], required for the ID of aggregate [${clazz.java.name}]."
        ) { idExtractor.apply(aggregateIdentifier) }

        return aggregateFactoryMethod.invoke(id)
    }

    override fun getAggregateType(): Class<A> = clazz.java

}

/**
 * Type alias for function creating the aggregate from id.
 */
typealias AggregateFactoryMethod<ID, A> = (ID) -> A
