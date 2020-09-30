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
import org.axonframework.extension.kotlin.example.AxonKotlinExampleApplication
import org.axonframework.extension.kotlin.example.api.*
import org.axonframework.extension.kotlin.spring.AggregateWithImmutableIdentifier
import org.axonframework.extensions.kotlin.send
import org.axonframework.modelling.command.AggregateCreationPolicy
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle.apply
import org.axonframework.modelling.command.CreationPolicy
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.util.*

/**
 * Bank configuration.
 */
@Configuration
class BankConfiguration(val bankAccountService: BankAccountService) {


//    /**
//     * Example of manual configuration.
//     */
//    @Autowired
//    fun manualConfigure(configurer: Configurer) {
//        configurer.configureAggregate(
//                defaultAggregateConfiguration<BankAccount>()
//                        .configureRepository { config ->
//                            EventSourcingAggregateWithImmutableIdentifierRepository(
//                                    EventSourcingRepository
//                                            .builder(BankAccount::class.java)
//                                            .eventStore(config.eventStore())
//                                            .aggregateFactory(usingIdentifier(UUID::class) { UUID.fromString(it) })
//                            )
//                        }
//        )
//    }

    /**
     * Application runner of bank ops.
     */
    @Bean
    fun accountOperationsRunner() = ApplicationRunner {
        bankAccountService.accountOperations()
    }
}

/**
 * Bank service.
 */
@Service
class BankAccountService(val commandGateway: CommandGateway) {

    companion object : KLogging()

    /**
     * Bank ops.
     */
    fun accountOperations() {
        val accountId = UUID.randomUUID().toString()

        logger.info { "\nPerforming basic operations on account $accountId" }

        commandGateway.send(
                command = CreateBankAccountCommand(accountId, 100),
                onSuccess = { _, result: Any?, _ ->
                    AxonKotlinExampleApplication.logger.info { "Successfully created account with id: $result" }
                    commandGateway.send(
                            command = DepositMoneyCommand(accountId, 20),
                            onSuccess = { c, _: Any?, _ -> logger.info { "Successfully deposited ${c.payload.amountOfMoney}" } },
                            onError = { c, e, _ -> logger.error(e) { "Error depositing money on ${c.payload.bankAccountId}" } }
                    )
                },
                onError = { c, e, _ -> logger.error(e) { "Error creating account ${c.payload.bankAccountId}" } }
        )

    }
}

/**
 * Bank account aggregate as data class.
 */
@AggregateWithImmutableIdentifier
data class BankAccount(
        @AggregateIdentifier
        private val id: UUID
) {

    private var overdraftLimit: Long = 0
    private var balanceInCents: Long = 0

    /**
     * Creates account.
     */
    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    fun create(command: CreateBankAccountCommand): String {
        apply(BankAccountCreatedEvent(command.bankAccountId, command.overdraftLimit))
        return command.bankAccountId
    }

    /**
     * Deposits money to account.
     */
    @CommandHandler
    fun deposit(command: DepositMoneyCommand) {
        apply(MoneyDepositedEvent(id.toString(), command.amountOfMoney))
    }

    /**
     * Withdraw money from account.
     */
    @CommandHandler
    fun withdraw(command: WithdrawMoneyCommand) {
        if (command.amountOfMoney <= balanceInCents + overdraftLimit) {
            apply(MoneyWithdrawnEvent(id.toString(), command.amountOfMoney))
        }
    }

    /**
     * Return money from account.
     */
    @CommandHandler
    fun returnMoney(command: ReturnMoneyOfFailedBankTransferCommand) {
        apply(MoneyOfFailedBankTransferReturnedEvent(id.toString(), command.amount))
    }

    /**
     * Handler to initialize bank accounts attributes.
     */
    @EventSourcingHandler
    fun on(event: BankAccountCreatedEvent) {
        overdraftLimit = event.overdraftLimit
        balanceInCents = 0
    }

    /**
     * Handler adjusting balance.
     */
    @EventSourcingHandler
    fun on(event: MoneyAddedEvent) {
        balanceInCents += event.amount
    }

    /**
     * Handler adjusting balance.
     */
    @EventSourcingHandler
    fun on(event: MoneySubtractedEvent) {
        balanceInCents -= event.amount
    }

}