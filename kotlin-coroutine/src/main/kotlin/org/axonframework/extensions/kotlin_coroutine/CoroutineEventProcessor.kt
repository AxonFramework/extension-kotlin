/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.extensions.kotlin_coroutine

import kotlinx.coroutines.*
import mu.KotlinLogging
import org.axonframework.eventhandling.TrackedEventMessage
import org.axonframework.eventhandling.TrackingToken
import org.axonframework.eventhandling.async.SequencingPolicy
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy
import org.axonframework.eventhandling.tokenstore.TokenStore
import org.axonframework.messaging.StreamableMessageSource
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val logger = KotlinLogging.logger {}

class CoroutineEventProcessor(
    private val processorName: String,
    private val messageSource: StreamableMessageSource<TrackedEventMessage<Any>>,
    private val tokenStore: TokenStore,
    private val segmentsSize: Int = 4,
    private val concurrentPerSegment: Int = 1,
    private val delayWhenStreamEmpty: Duration = 100.toDuration(DurationUnit.MILLISECONDS),
    private val tokenClaimInterval: Duration = 5.toDuration(DurationUnit.SECONDS),
    private val coordinatorContext: CoroutineContext = Dispatchers.Default,
    private val workerContext: CoroutineContext = Dispatchers.Default,
    private val initialTrackingTokenBuilder: Function1<StreamableMessageSource<TrackedEventMessage<Any>>, TrackingToken?> =
        StreamableMessageSource<TrackedEventMessage<Any>>::createTailToken,
    private val strategy: Worker.Strategy = Worker.Strategy.AtLeastOnce,
    private val sequencingPolicy: SequencingPolicy<in TrackedEventMessage<Any>> = SequentialPerAggregatePolicy.instance(),
    private val exceptionHandler: ProcessingErrorHandler = propagatingErrorHandler,
    private val coordinatorBatchSize: Int = 1024,
    private val workerBufferSize: Int = 1024,
    private val delayWhenBufferFull: Duration = 5.toDuration(DurationUnit.SECONDS),
) {
    private val state: AtomicReference<State> = AtomicReference(State.NOT_STARTED)
    private val tasks: MutableList<ProcessorTask> = mutableListOf()
    private val coordinateJob: AtomicReference<Deferred<Unit>> = AtomicReference(null)

    fun addTask(task: ProcessorTask) {
        tasks.add(task)
    }

    suspend fun start() {
        if (state.compareAndSet(State.NOT_STARTED, State.STARTED)) {
            if (tokenStore.fetchSegments(processorName).isEmpty()) {
                logger.info { "Initializing token store for processor with name: $processorName." }
                val initialToken: TrackingToken = initialTrackingTokenBuilder.invoke(messageSource) ?: FirstToken
                tokenStore.initializeTokenSegments(processorName, segmentsSize, initialToken)
            }
            logger.info { "Creating and starting coordinator." }
            val coordinator = Coordinator(
                processorName = processorName,
                messageSource = messageSource,
                tasks = tasks.toList(),
                tokenStore = tokenStore,
                concurrentPerSegment = concurrentPerSegment,
                delayWhenStreamEmpty = delayWhenStreamEmpty,
                tokenClaimInterval = tokenClaimInterval,
                workerContext = workerContext,
                state = state,
                strategy = strategy,
                sequencingPolicy = sequencingPolicy,
                exceptionHandler = exceptionHandler,
                batchSize = coordinatorBatchSize,
                workerBufferSize = workerBufferSize,
                delayWhenBufferFull = delayWhenBufferFull,
            )
            coordinateJob.set(CoroutineScope(coordinatorContext).async { coordinator.coordinate() })
        } else {
            logger.info("Not starting because current state is: $state.")
        }
    }

    suspend fun stop() {
        logger.info { "Stopping coordinator" }
        state.set(State.SHUT_DOWN)
        coordinateJob.getAndSet(null)?.cancelAndJoin()
        logger.info { "Coordinator stopped" }
    }

    /**
     * Enum representing the possible states of the Processor
     */
    enum class State(val isRunning: Boolean) {
        /**
         * Indicates the processor has not been started yet.
         */
        NOT_STARTED(false),

        /**
         * Indicates that the processor has started and is (getting ready to) processing events
         */
        STARTED(true),

        /**
         * Indicates that the processor has been paused. It can be restarted to continue processing.
         */
        PAUSED(false),

        /**
         * Indicates that the processor has been shut down. It can be restarted to continue processing.
         */
        SHUT_DOWN(false),

        /**
         * Indicates that the processor has been paused due to an error that it was unable to recover from. Restarting
         * is possible once the error has been resolved.
         */
        PAUSED_ERROR(false);
    }
}

data class ProcessorTask(
    val filter: Predicate<TrackedEventMessage<Any>>,
    val task: suspend (TrackedEventMessage<Any>) -> Unit
)