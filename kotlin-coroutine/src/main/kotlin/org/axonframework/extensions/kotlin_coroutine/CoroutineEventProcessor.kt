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
    private val sequencingPolicy: SequencingPolicy<in TrackedEventMessage<Any>> = SequentialPerAggregatePolicy.instance()
) {
    private val state: AtomicReference<State> = AtomicReference(State.NOT_STARTED)
    private val tasks: MutableList<ProcessorTask> = mutableListOf()
    private val coordinateJob: AtomicReference<Deferred<Unit>> = AtomicReference(null)

    fun addTask(task: ProcessorTask) {
        tasks.add(task)
    }

    suspend fun start() {
        if (tokenStore.fetchSegments(processorName).isEmpty()) {
            val initialToken: TrackingToken = initialTrackingTokenBuilder.invoke(messageSource) ?: FirstToken
            tokenStore.initializeTokenSegments(processorName, segmentsSize, initialToken)
        }
        if (state.compareAndSet(State.NOT_STARTED, State.STARTED)) {
            logger.info("Creating and starting coordinator.")
            val coordinator = Coordinator(
                processorName,
                messageSource,
                tasks.toList(),
                tokenStore,
                concurrentPerSegment,
                delayWhenStreamEmpty,
                tokenClaimInterval,
                workerContext,
                state,
                strategy,
                sequencingPolicy
            )
            coordinateJob.set(CoroutineScope(coordinatorContext).async { coordinator.coordinate() })
        }
    }

    suspend fun stop() {
        state.set(State.SHUT_DOWN)
        logger.info("Stopping coordinator")
        coordinateJob.getAndSet(null)?.cancelAndJoin()
        logger.info("Coordinator stopped")
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