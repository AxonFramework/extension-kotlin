/*
 * Copyright (c) 2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.extensions.kotlin.aggregate

import org.axonframework.extensions.kotlin.TestLongAggregate
import org.axonframework.extensions.kotlin.TestStringAggregate
import org.axonframework.extensions.kotlin.TestUUIDAggregate
import org.axonframework.extensions.kotlin.aggregate.ImmutableIdentifierAggregateFactory.Companion.usingIdentifier
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


/**
 * Test for the aggregate factory.
 *
 * @author Simon Zambrovski
 */
internal class AggregateWithImmutableIdentifierFactoryTest {

    @Test
    fun `should create string aggregate`() {
        val aggregateId = UUID.randomUUID().toString()
        val factory = ImmutableIdentifierAggregateFactory.usingStringIdentifier<TestStringAggregate>()
        val aggregate = factory.createAggregateRoot(aggregateId, null)

        assertEquals(aggregateId, aggregate.aggregateId)
    }

    @Test
    fun `should create uuid aggregate`() {
        val aggregateId = UUID.randomUUID()
        val factory: ImmutableIdentifierAggregateFactory<TestUUIDAggregate, UUID> = usingIdentifier(UUID::class) { UUID.fromString(it) }
        val aggregate = factory.createAggregateRoot(aggregateId.toString(), null)

        assertEquals(aggregateId, aggregate.aggregateId)
    }

    @Test
    fun `should fail create aggregate with wrong constructor type`() {
        val aggregateId = UUID.randomUUID()
        // pretending the TestLongAggregate to have UUID as identifier.
        val factory: ImmutableIdentifierAggregateFactory<TestLongAggregate, UUID> = usingIdentifier(UUID::class) { UUID.fromString(it) }

        val exception = assertFailsWith<IllegalArgumentException> {
            factory.createAggregateRoot(aggregateId.toString(), null)
        }

        assertEquals(exception.message,
            "The aggregate [${factory.aggregateType.name}] doesn't provide a constructor for the identifier type [${UUID::class.java.name}].")
    }

    @Test
    fun `should fail create aggregate error in extractor`() {
        val aggregateId = UUID.randomUUID()
        // the extractor is broken.
        val factory: ImmutableIdentifierAggregateFactory<TestUUIDAggregate, UUID> = usingIdentifier(UUID::class) { throw java.lang.IllegalArgumentException("") }

        val exception = assertFailsWith<IllegalArgumentException> {
            factory.createAggregateRoot(aggregateId.toString(), null)
        }

        assertEquals(exception.message,
            "The identifier [$aggregateId] could not be converted to the type [${UUID::class.java.name}], required for the ID of aggregate [${factory.aggregateType.name}].")
    }

}