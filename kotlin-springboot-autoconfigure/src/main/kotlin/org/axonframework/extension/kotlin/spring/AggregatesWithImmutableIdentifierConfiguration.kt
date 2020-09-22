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
package org.axonframework.extension.kotlin.spring

import mu.KLogging
import org.axonframework.config.AggregateConfigurer.defaultConfiguration
import org.axonframework.config.Configurer
import org.axonframework.eventsourcing.EventSourcingRepository
import org.axonframework.extensions.kotlin.aggregate.AggregateIdentifierConverter
import org.axonframework.extensions.kotlin.aggregate.AggregateWithImmutableIdentifierFactory.Companion.usingIdentifier
import org.axonframework.extensions.kotlin.aggregate.AggregateWithImmutableIdentifierFactory.Companion.usingStringIdentifier
import org.axonframework.extensions.kotlin.aggregate.AggregateWithImmutableIdentifierFactory.Companion.usingUUIDIdentifier
import org.axonframework.extensions.kotlin.aggregate.EventSourcingAggregateWithImmutableIdentifierRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

/**
 * Configuration to activate the aggregate with immutable identifier detection and registration of the corresponding factories and repositories.
 * @see EnableAggregateWithImmutableIdentifierScan for activation.
 *
 * @author Simon Zambrovski
 * @since 0.2.0
 */
@Configuration
open class AggregatesWithImmutableIdentifierConfiguration(
    private val context: ApplicationContext
) {

    companion object : KLogging()

    /**
     * Initializes the settings.
     * @return settings.
     */
    @Bean
    @ConditionalOnMissingBean
    open fun initialize(): AggregatesWithImmutableIdentifierSettings {
        val beans = context.getBeansWithAnnotation(EnableAggregateWithImmutableIdentifierScan::class.java)
        require(beans.isNotEmpty()) {
            "EnableAggregateWithImmutableIdentifierScan should be activated exactly once."
        }
        require(beans.size == 1) {
            "EnableAggregateWithImmutableIdentifierScan should be activated exactly once, but was found on ${beans.size} beans:\n" + beans.map { it.key }.joinToString()
        }
        val basePackage = EnableAggregateWithImmutableIdentifierScan.getBasePackage(beans.entries.first().value)
        return AggregatesWithImmutableIdentifierSettings(basePackage = basePackage
            ?: throw IllegalStateException("Required setting basePackage could not be initialized, consider to provide your own AggregatesWithImmutableIdentifierSettings.")
        )
    }

    @Autowired
    fun configureAggregates(
        configurer: Configurer,
        settings: AggregatesWithImmutableIdentifierSettings,
        @Autowired(required = false) identifierConverters: List<AggregateIdentifierConverter<*>>?
    ) {
        val converters = identifierConverters ?: emptyList() // fallback to empty list if none are defined

        logger.info { "Discovered ${converters.size} converters for aggregate identifiers." }
        logger.info { "Scanning ${settings.basePackage} for aggregates" }
        AggregateWithImmutableIdentifier::class
            .findAnnotatedAggregateClasses(settings.basePackage)
            .map { aggregateClazz ->
                configurer.configureAggregate(
                    defaultConfiguration(aggregateClazz.java)
                        .configureRepository { config ->
                            EventSourcingAggregateWithImmutableIdentifierRepository(
                                builder = EventSourcingRepository
                                    .builder(aggregateClazz.java)
                                    .eventStore(config.eventStore())
                                    .aggregateFactory(
                                        when (val idFieldClazz = aggregateClazz.extractAggregateIdentifierClass()) {
                                            String::class -> usingStringIdentifier(aggregateClazz)
                                            UUID::class -> usingUUIDIdentifier(aggregateClazz)
                                            else -> usingIdentifier(aggregateClazz, idFieldClazz, converters.findIdentifierConverter(idFieldClazz))
                                        }.also {
                                            logger.info { "Registering aggregate factory $it" }
                                        }
                                    )
                            )
                        }
                )
            }
    }


}

