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

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.eventsourcing.AggregateFactory
import org.axonframework.eventsourcing.EventSourcingRepository
import org.axonframework.eventsourcing.eventstore.DomainEventStream
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.extensions.kotlin.TestStringAggregate
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.Callable
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.fail

/**
 * Test for the aggregate repository using the factory.
 *
 * @author Simon Zambrovski
 */
internal class EventSourcingAggregateWithImmutableIdentifierRepositoryTest {

    private val aggregateIdentifier = UUID.randomUUID().toString()
    private val eventStore = mockk<EventStore>()
    private val aggregateFactory = mockk<AggregateFactory<TestStringAggregate>>()
    private lateinit var uow: DefaultUnitOfWork<*>

    @BeforeTest
    fun `init components`() {
        uow = DefaultUnitOfWork.startAndGet(GenericCommandMessage<String>("some payload"))

        every { aggregateFactory.aggregateType }.returns(TestStringAggregate::class.java)
        every { aggregateFactory.createAggregateRoot(aggregateIdentifier, null) }.returns(TestStringAggregate(aggregateIdentifier))

        // no events
        every { eventStore.readEvents(aggregateIdentifier) }.returns(DomainEventStream.empty())
    }

    @AfterTest
    fun `check uow`() {
        assertEquals(UnitOfWork.Phase.STARTED, uow.phase())
    }

    @Test
    fun `should ask factory to create the aggregate`() {


        val repo = EventSourcingAggregateWithImmutableIdentifierRepository<TestStringAggregate>(
            EventSourcingRepository
                .builder(TestStringAggregate::class.java)
                .eventStore(eventStore)
                .aggregateFactory(aggregateFactory)
        )

        val factoryMethod = Callable<TestStringAggregate> {
            fail("The factory method should not be called.")
        }
        val aggregate = repo.loadOrCreate(aggregateIdentifier = aggregateIdentifier, factoryMethod = factoryMethod)

        assertEquals(aggregateIdentifier, aggregate.identifierAsString())
        verify {
            aggregateFactory.createAggregateRoot(aggregateIdentifier, null)
        }

    }
}