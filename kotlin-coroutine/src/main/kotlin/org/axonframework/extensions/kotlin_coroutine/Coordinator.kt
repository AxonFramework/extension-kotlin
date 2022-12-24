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

import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.axonframework.common.ObjectUtils
import org.axonframework.common.io.IOUtils.closeQuietly
import org.axonframework.common.stream.BlockingStream
import org.axonframework.eventhandling.Segment
import org.axonframework.eventhandling.TrackedEventMessage
import org.axonframework.eventhandling.TrackingToken
import org.axonframework.eventhandling.async.SequencingPolicy
import org.axonframework.eventhandling.tokenstore.TokenStore
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException
import org.axonframework.messaging.StreamableMessageSource
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

class Coordinator(
    private val processorName: String,
    private val messageSource: StreamableMessageSource<TrackedEventMessage<Any>>,
    private val tasks: List<ProcessorTask>,
    private val tokenStore: TokenStore,
    private val concurrentPerSegment: Int,
    private val delayWhenStreamEmpty: Duration,
    private val tokenClaimInterval: Duration,
    private val workerContext: CoroutineContext,
    private val state: AtomicReference<CoroutineEventProcessor.State>,
    private val strategy: Worker.Strategy,
    private val sequencingPolicy: SequencingPolicy<in TrackedEventMessage<Any>>,
    private val exceptionHandler: ProcessingErrorHandler,
    private val batchSize: Int,
    private val workerBufferSize: Int,
    private val delayWhenBufferFull: Duration,
) {
    private val activeSegments: MutableMap<Segment, Worker> = ConcurrentHashMap()
    private var eventStream: BlockingStream<TrackedEventMessage<Any>>? = null
    private var lastScheduledToken: TrackingToken = NoToken
    private var lastCheckedUnclaimedSegments = Instant.ofEpochMilli(0L)
    private var lastWorker: Worker? = null

    suspend fun coordinate() {
        while (state.get().isRunning) {
            if (lastCheckedUnclaimedSegments.isBefore(Instant.now().minusSeconds(tokenClaimInterval.inWholeSeconds))) {
                logger.debug { "Updating active segments." }
                cleanInactive()
                checkUnclaimedSegments()
                logger.debug { "Active segments: ${activeSegments.keys.map { it.segmentId }.sorted()}." }
            }
            if (activeSegments.isEmpty()) {
                logger.debug { "No active segments, so start delay." }
                delay(tokenClaimInterval)
            } else if (eventStream?.hasNextAvailable() != true) {
                logger.debug { "No new events, so start delay." }
                delay(delayWhenStreamEmpty)
            } else {
                logger.debug { "Coordinating the next batch of events." }
                eventStream?.let {
                    processStream(it)
                }
            }
        }
        activeSegments.values.forEach { it.stop() }
    }

    private suspend fun processStream(stream: BlockingStream<TrackedEventMessage<Any>>) {
        var fetched = 0
        var nextHasSameToken = false
        while (nextHasSameToken || (fetched < batchSize && stream.hasNextAvailable())) {
            val event: TrackedEventMessage<Any> = stream.nextAvailable()
            logger.debug { "Coordinate event with id: [${event.identifier}]." }
            fetched++
            nextHasSameToken = stream.peek()
                .filter { e: TrackedEventMessage<Any> -> lastScheduledToken == e.trackingToken() }
                .isPresent
            try {
                scheduleEvent(event, nextHasSameToken)
            } catch (e: ProcessingChannelFullOrClosedException) {
                logger.warn("Failed to schedule event.", e)
                eventStream = messageSource.openStream(lastScheduledToken)
                delay(delayWhenBufferFull)
                return
            }
            if (!nextHasSameToken) {
                lastScheduledToken = event.trackingToken()
            }
        }
    }

    private fun scheduleEvent(event: TrackedEventMessage<Any>, nextHasSameToken: Boolean) {
        tasks.forEach { processorTask ->
            if (processorTask.filter.test(event)) {
                lastWorker?.let {
                    it.take(processorTask, event, nextHasSameToken)
                    if (!nextHasSameToken) {
                        lastWorker = null
                    }
                    return
                }
                selectWorker(event)?.let {
                    it.take(processorTask, event, nextHasSameToken)
                    if (nextHasSameToken) {
                        lastWorker = it
                    }
                    return
                }
            }
        }
    }


    private fun checkUnclaimedSegments() {
        val newToken = updateActiveSegments()
        newToken?.let { token ->
            eventStream = eventStream?.let {
                if (token != lastScheduledToken) {
                    closeQuietly(it)
                    messageSource.open(token)
                } else {
                    it
                }
            } ?: messageSource.open(token)
            lastScheduledToken = token
        }
        lastCheckedUnclaimedSegments = Instant.now()
    }

    private fun cleanInactive() {
        logger.debug { "Removing inactive segments." }
        activeSegments.iterator().forEach {
            if (!it.value.isActive()) {
                activeSegments.remove(it.key)
                logger.info { "Removed as active segment: [${it.key.segmentId}]." }
            }
        }
    }

    private fun updateActiveSegments(): TrackingToken? {
        logger.debug { "Updating active segments, possibly adding unclaimed ones." }
        var result: TrackingToken? = null
        tokenStore.fetchAvailableSegments(processorName)
            .filter { !activeSegments.containsKey(it) }
            .forEach {
                try {
                    val token = tokenStore.fetchToken(processorName, it) ?: FirstToken
                    result = result?.lowerBound(token) ?: token
                    val worker = Worker(
                        processorName = processorName,
                        tokenStore = tokenStore,
                        concurrent = concurrentPerSegment,
                        segment = it,
                        tokenClaimInterval = tokenClaimInterval,
                        workerContext = workerContext,
                        currentToken = token,
                        strategy = strategy,
                        exceptionHandler = exceptionHandler,
                        bufferSize = workerBufferSize,
                    )
                    worker.start()
                    activeSegments[it] = worker
                    logger.info { "Added new segment: [${it.segmentId}]." }
                } catch (e: UnableToClaimTokenException) {
                    logger.info("Failed to fetch token.", e)
                }
            }
        return result
    }

    private fun selectWorker(event: TrackedEventMessage<Any>): Worker? {
        val hash = Objects.hashCode(sequenceIdentifier(event))
        logger.debug { "Selecting worker for hash: [$hash], ${activeSegments.keys.size} workers available." }
        activeSegments.forEach { (segment, worker) ->
            if (segment.matches(hash)) {
                logger.debug { "Worker for segment: [${segment.segmentId}] matched." }
                return worker
            }
        }
        logger.debug { "Could not find worker for hash: [$hash]." }
        return null
    }

    private fun sequenceIdentifier(event: TrackedEventMessage<Any>): Any? {
        return ObjectUtils.getOrDefault(sequencingPolicy.getSequenceIdentifierFor(event)) { event.identifier }
    }
}

private fun StreamableMessageSource<TrackedEventMessage<Any>>.open(token: TrackingToken): BlockingStream<TrackedEventMessage<Any>> {
    logger.info { "Opening new stream with token: [{$token}]" }
    return if (token::class == FirstToken::class) {
        this.openStream(null)
    } else {
        this.openStream(token)
    }
}

object FirstToken : TrackingToken {

    override fun lowerBound(other: TrackingToken): TrackingToken {
        return this
    }

    override fun upperBound(other: TrackingToken): TrackingToken {
        return other
    }

    override fun covers(other: TrackingToken): Boolean {
        return false
    }
}

object NoToken : TrackingToken {

    override fun lowerBound(other: TrackingToken): TrackingToken {
        return other
    }

    override fun upperBound(other: TrackingToken): TrackingToken {
        return other
    }

    override fun covers(other: TrackingToken): Boolean {
        return false
    }
}