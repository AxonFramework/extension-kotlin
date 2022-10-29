package org.axonframework.extensions.kotlin_coroutine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.axonframework.eventhandling.Segment
import org.axonframework.eventhandling.TrackedEventMessage
import org.axonframework.eventhandling.TrackingToken
import org.axonframework.eventhandling.tokenstore.TokenStore
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val logger = KotlinLogging.logger {}
class Worker(
    private val processorName: String,
    private val tokenStore: TokenStore,
    private val concurrent: Int,
    private val segment: Segment,
    private val tokenClaimInterval: Duration,
    private val workerContext: CoroutineContext,
    private var currentToken: TrackingToken,
    private val strategy: Strategy
) {
    private val active = AtomicBoolean(false)
    private var lowestToken = currentToken
    private var highestToken = currentToken
    private val activeTokens: Queue<TrackingToken> = ConcurrentLinkedQueue()
    private val processingQueue: Queue<ProcessingPackage> = ConcurrentLinkedQueue()
    private var processingPackage: ProcessingPackage? = null
    private val activePackages = AtomicInteger(0)
    private val bufferSize = 1024

    fun take(processorTask: ProcessorTask, event: TrackedEventMessage<Any>, nextHasSameToken: Boolean) {
        if (!active.get()) {
            logger.debug("Not taking message with id [{}] because not active.", event.identifier)
            return
        }
        if (highestToken.covers(event.trackingToken())) {
            logger.debug("Not taking message with id [{}] because token indicates it's already processed.", event.identifier)
            return
        }
        val entry = ProcessingEntry(processorTask, event)
        processingPackage = processingPackage?.let {
            ProcessingPackage(it.entries + listOf(entry))
        } ?: ProcessingPackage(listOf(entry))
        if (!nextHasSameToken) {
            logger.debug("Adding package with {} messages to queue.", processingPackage!!.entries.size)
            processingQueue.add(processingPackage)
            processingPackage = null
        }
    }

    fun isActive(): Boolean {
        return active.get()
    }

    fun isFull(): Boolean {
        return processingQueue.size > bufferSize
    }

    private fun processingStarting(token: TrackingToken) {
        highestToken = token
        activeTokens.add(token)
        when (strategy) {
            Strategy.AtMostOnce -> {
                currentToken = highestToken
                store()
            }
            Strategy.AtLeastOnce -> {
                //nothing needs to happen
            }
        }
    }

    private fun processingCompleted(token: TrackingToken) {
        activeTokens.remove(token)
        lowestToken = if (activeTokens.isEmpty()) {
            lowestToken.upperBound(token)
        } else {
            activeTokens.peek()
        }
        when (strategy) {
            Strategy.AtMostOnce -> {
                //nothing needs to happen
            }
            Strategy.AtLeastOnce -> {
                currentToken = lowestToken
            }
        }
        activePackages.decrementAndGet()
    }

    private fun store() {
        try {
            tokenStore.storeToken(currentToken, processorName, segment.segmentId)
        } catch (e: UnableToClaimTokenException) {
            logger.info("Failed to claim token.", e)
            stop()
        }
    }

    private suspend fun claimLoop() {
        delay(tokenClaimInterval)
        while (active.get()) {
            store()
            delay(tokenClaimInterval)
        }
    }

    private suspend fun processLoop() {
        while (active.get()) {
            if (activePackages.get() < concurrent && !processingQueue.isEmpty()) {
                logger.debug("Start processing package")
                activePackages.incrementAndGet()
                processPackage(processingQueue.poll())
            } else {
                delay(50.toDuration(DurationUnit.MILLISECONDS))
            }
        }
    }

    private suspend fun processPackage(processingPackage: ProcessingPackage) {
        val token = processingPackage.entries[0].message.trackingToken()
        processingStarting(token)
        CoroutineScope(workerContext).launch {
            processingPackage.entries.forEach {
                it.processorTask.task.invoke(it.message)
            }
            processingCompleted(token)
        }
    }

    fun start() {
        logger.info("Worker started")
        if (active.compareAndSet(false, true)) {
            CoroutineScope(workerContext).launch { processLoop() }
            CoroutineScope(workerContext).launch { claimLoop() }
        }
    }

    fun stop() {
        active.set(false)
    }

    enum class Strategy {
        AtMostOnce,
        AtLeastOnce
    }
}

data class ProcessingEntry(
    val processorTask: ProcessorTask,
    val message: TrackedEventMessage<Any>,
)

data class ProcessingPackage(
    val entries: List<ProcessingEntry>
)