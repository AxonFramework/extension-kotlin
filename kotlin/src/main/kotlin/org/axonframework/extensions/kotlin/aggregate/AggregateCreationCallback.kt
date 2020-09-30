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

/**
 * Informs about creation of an aggregate.
 */
@FunctionalInterface
interface AggregateCreationCallback<A: Any> {

    /**
     * Callback method called after the aggregate instance has been created.
     * @param aggregateInstance aggregate instance.
     * @param aggregateFactory factory created the aggregate.
     */
    fun aggregateCreated(aggregateInstance: A, aggregateFactory: AggregateWithImmutableIdentifierFactory<*, *>): Unit
}