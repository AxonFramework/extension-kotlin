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
package org.axonframework.extension.kotlin.example

import mu.KLogging
import org.axonframework.eventhandling.tokenstore.TokenStore
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine
import org.axonframework.extension.kotlin.spring.EnableAggregateWithImmutableIdentifierScan
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

/**
 * Starting point.
 * @param args CLI parameters.
 */
fun main(args: Array<String>) {
    SpringApplication.run(AxonKotlinExampleApplication::class.java, *args)
}

@SpringBootApplication
@EnableAggregateWithImmutableIdentifierScan
class AxonKotlinExampleApplication {

    companion object : KLogging()

    /**
     * Configures to use in-memory embedded event store.
     */
    @Bean
    fun eventStore(): EventStore = EmbeddedEventStore.builder().storageEngine(InMemoryEventStorageEngine()).build()

    /**
     * Configures to use in-memory token store.
     */
    @Bean
    fun tokenStore(): TokenStore = InMemoryTokenStore()

}