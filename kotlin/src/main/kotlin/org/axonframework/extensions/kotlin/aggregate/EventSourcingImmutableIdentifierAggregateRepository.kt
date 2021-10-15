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

import org.axonframework.eventsourcing.EventSourcedAggregate
import org.axonframework.eventsourcing.EventSourcingRepository
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork
import org.axonframework.modelling.command.Aggregate
import org.axonframework.modelling.command.LockAwareAggregate
import java.util.concurrent.Callable

/**
 * Event souring repository which uses a aggregate factory to create new aggregate passing <code>null</code> as first event.
 * @param builder repository builder with configuration.
 *
 * @since 0.2.0
 * @author Simon Zambrovski
 */
class EventSourcingImmutableIdentifierAggregateRepository<A>(
    builder: Builder<A>
) : EventSourcingRepository<A>(builder) {

    override fun loadOrCreate(aggregateIdentifier: String, factoryMethod: Callable<A>): Aggregate<A> {
        val factory = super.getAggregateFactory()
        val uow = CurrentUnitOfWork.get()
        val aggregates: MutableMap<String, LockAwareAggregate<A, EventSourcedAggregate<A>>> = managedAggregates(uow)
        val aggregate = aggregates.computeIfAbsent(aggregateIdentifier) { aggregateId: String ->
            try {
                return@computeIfAbsent doLoadOrCreate(aggregateId) {
                    // call the factory and instead of newInstance on the aggregate class
                    factory.createAggregateRoot(aggregateId, null)
                }
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        uow.onRollback { aggregates.remove(aggregateIdentifier) }
        prepareForCommit(aggregate)
        return aggregate
    }
}