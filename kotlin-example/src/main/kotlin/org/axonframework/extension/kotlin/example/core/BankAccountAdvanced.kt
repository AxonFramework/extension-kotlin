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
package org.axonframework.extension.kotlin.example.core

import mu.KLogging
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.extension.kotlin.example.api.AdvancedBankAccountCreatedEvent
import org.axonframework.extension.kotlin.example.api.CreateAdvancedBankAccountCommand
import org.axonframework.extension.kotlin.spring.AggregateWithImmutableIdentifier
import org.axonframework.extensions.kotlin.aggregate.AggregateIdentifierConverter
import org.axonframework.extensions.kotlin.aggregate.ImmutableAggregateIdentifier
import org.axonframework.extensions.kotlin.send
import org.axonframework.modelling.command.AggregateCreationPolicy
import org.axonframework.modelling.command.AggregateLifecycle.apply
import org.axonframework.modelling.command.CreationPolicy
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import java.util.*

/**
 * Advanced bank config.
 */
@Configuration
class AdvancedBankConfiguration(val advancedBankAccountService: AdvancedBankAccountService) {

    /**
     * Application run starting bank ops.
     */
    @Bean
    fun advancedAccountOperationsRunner() = ApplicationRunner {
        advancedBankAccountService.accountOperations()
    }

    /**
     * Bank identifier converter.
     */
    @Bean
    fun bankIdentifierConverter() = object : AggregateIdentifierConverter<BankAccountIdentifier> {
        override fun apply(aggregateIdentifier: String) = BankAccountIdentifier(aggregateIdentifier.subSequence(3, aggregateIdentifier.length - 3).toString())
    }

    /**
     * Long converter (not used), should remain to demonstrate correct converter selection.
     */
    @Bean
    fun longConverter() = object : AggregateIdentifierConverter<Long> {
        override fun apply(aggregateIdentifier: String) = aggregateIdentifier.toLong()
    }

}

/**
 * Advanced bank service.
 */
@Service
class AdvancedBankAccountService(val commandGateway: CommandGateway) {

    companion object : KLogging()

    /**
     * Runs account ops.
     */
    fun accountOperations() {

        val accountIdAdvanced = BankAccountIdentifier(UUID.randomUUID().toString())
        logger.info { "\nPerforming advanced operations on account $accountIdAdvanced" }

        commandGateway.send(
            command = CreateAdvancedBankAccountCommand(accountIdAdvanced, 100),
            onSuccess = { _, result: Any?, _ ->
                logger.info { "Successfully created account with id: $result" }
            },
            onError = { c, e, _ -> logger.error(e) { "Error creating account ${c.payload.bankAccountId}" } }
        )
    }
}


/**
 * Value type for bank account identifier.
 */
data class BankAccountIdentifier(val id: String) {
    override fun toString(): String = "<<<$id>>>"
}

/**
 * Aggregate using a complex type as identifier.
 */
@AggregateWithImmutableIdentifier
data class BankAccountAdvanced(
    @ImmutableAggregateIdentifier
    private val id: BankAccountIdentifier
) {

    private var overdraftLimit: Long = 0
    private var balanceInCents: Long = 0

    /**
     * Create command handler.
     */
    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    fun create(command: CreateAdvancedBankAccountCommand): BankAccountIdentifier {
        apply(AdvancedBankAccountCreatedEvent(command.bankAccountId, command.overdraftLimit))
        return command.bankAccountId
    }


    /**
     * Handler to initialize bank accounts attributes.
     */
    @EventSourcingHandler
    fun on(event: AdvancedBankAccountCreatedEvent) {
        overdraftLimit = event.overdraftLimit
        balanceInCents = 0
    }
}

