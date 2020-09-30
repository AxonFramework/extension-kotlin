package org.axonframework.extensions.kotlin.aggregate

interface AggregateCreationCallback<A: Any> {
    fun aggregateCreated(aggregateInstance: A): Unit
}