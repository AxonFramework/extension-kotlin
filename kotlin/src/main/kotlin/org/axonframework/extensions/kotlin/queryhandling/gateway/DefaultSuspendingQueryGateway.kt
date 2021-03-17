package org.axonframework.extensions.kotlin.queryhandling.gateway

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingleOrNull
import kotlinx.coroutines.stream.consumeAsFlow
import org.axonframework.common.BuilderUtils
import org.axonframework.common.Registration
import org.axonframework.extensions.kotlin.messaging.SuspendingMessageDispatchInterceptor
import org.axonframework.extensions.kotlin.messaging.SuspendingResultHandlerInterceptor
import org.axonframework.extensions.kotlin.messaging.payloadOrThrowException
import org.axonframework.messaging.GenericMessage
import org.axonframework.messaging.ResultMessage
import org.axonframework.messaging.responsetypes.ResponseType
import org.axonframework.queryhandling.*
import java.time.Duration
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Implementation of the [SuspendingQueryGateway].
 *
 * @author Joel Feinstein
 * @since 0.2.0
 */
class DefaultSuspendingQueryGateway(config: Builder.() -> Unit) : SuspendingQueryGateway {
    private val queryBus: QueryBus
    private val dispatchInterceptors: MutableList<SuspendingMessageDispatchInterceptor<QueryMessage<*, *>>>
    private val resultInterceptors: MutableList<SuspendingResultHandlerInterceptor<QueryMessage<*, *>, ResultMessage<*>>>

    init {
        Builder().apply(config).validate().let {
            queryBus = it.queryBus
            dispatchInterceptors = CopyOnWriteArrayList(it.dispatchInterceptors)
            resultInterceptors = CopyOnWriteArrayList(it.resultInterceptors)
        }
    }

    override fun registerDispatchInterceptor(interceptor: SuspendingMessageDispatchInterceptor<QueryMessage<*, *>>): Registration {
        dispatchInterceptors += interceptor
        return Registration { dispatchInterceptors.remove(interceptor) }
    }

    override fun registerResultHandlerInterceptor(interceptor: SuspendingResultHandlerInterceptor<QueryMessage<*, *>, ResultMessage<*>>): Registration {
        resultInterceptors += interceptor
        return Registration { resultInterceptors.remove(interceptor) }
    }

    override suspend fun <R, Q> query(queryName: String, query: Q, responseType: ResponseType<R>): R {
        val queryMessage = GenericQueryMessage(GenericMessage.asMessage(query), queryName, responseType).processDispatchInterceptors()
        return processResultsInterceptors(queryMessage, queryMessage.dispatch()).payloadOrThrowException()
    }

    override suspend fun <R, Q> scatterGather(queryName: String, query: Q, responseType: ResponseType<R>, timeout: Duration): Flow<R> {
        val queryMessage =  GenericQueryMessage(GenericMessage.asMessage(query), queryName, responseType).processDispatchInterceptors()
        return queryMessage.dispatchScatterGather(timeout.toMillis(), TimeUnit.MILLISECONDS).map {
            processResultsInterceptors(queryMessage, it).payloadOrThrowException()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <Q, I, U> subscriptionQuery(
        queryName: String,
        query: Q,
        initialResponseType: ResponseType<I>,
        updateResponseType: ResponseType<U>,
        backpressure: SubscriptionQueryBackpressure,
        updateBufferSize: Int
    ): SuspendingSubscriptionQueryResult<I, U> {
        val subscriptionQueryMessage = GenericSubscriptionQueryMessage(query, initialResponseType, updateResponseType).processDispatchInterceptors() as SubscriptionQueryMessage<Q, I, U>
        val subscriptionDelegate = subscriptionQueryMessage.dispatch(backpressure, updateBufferSize)

        return object : SuspendingSubscriptionQueryResult<I, U> {
            override suspend fun initialResult(): I {
                return processResultsInterceptors(subscriptionQueryMessage, subscriptionDelegate.initialResult().awaitSingleOrNull()).payloadOrThrowException()
            }

            override fun updates(): Flow<U> {
                return subscriptionDelegate.updates().asFlow().map { processResultsInterceptors(subscriptionQueryMessage, it).payloadOrThrowException() }
            }

            override fun cancel(): Boolean {
                return subscriptionDelegate.cancel()
            }
        }
    }

    private suspend fun QueryMessage<*, *>.dispatch(): ResultMessage<*> {
        return queryBus.query(this).await()
    }

    private fun QueryMessage<*, *>.dispatchScatterGather(timeout: Long, timeUnit: TimeUnit): Flow<ResultMessage<*>> {
        return queryBus.scatterGather(this, timeout, timeUnit).consumeAsFlow()
    }

    private fun <Q, I, U> SubscriptionQueryMessage<Q, I, U>.dispatch(
        backpressure: SubscriptionQueryBackpressure,
        updateBufferSize: Int
    ): SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> {
        return queryBus.subscriptionQuery(this, backpressure, updateBufferSize)
    }

    private suspend fun QueryMessage<*, *>.processDispatchInterceptors(): QueryMessage<*, *> {
        return dispatchInterceptors.fold(this) { acc, interceptor -> interceptor.intercept(acc) }
    }

    private suspend fun processResultsInterceptors(queryMessage: QueryMessage<*, *>, queryResultMessage: ResultMessage<*>): ResultMessage<*> {
        return resultInterceptors.fold(queryResultMessage) { acc, interceptor -> interceptor.intercept(queryMessage, acc) }
    }

    /**
     * Builder class to instantiate [DefaultReactorQueryGateway].
     *
     *
     * The `dispatchInterceptors` are defaulted to an empty list.
     * The [QueryBus] is a **hard requirement** and as such should be provided.
     *
     */
    class Builder internal constructor() {
        lateinit var queryBus: QueryBus
        val dispatchInterceptors: List<SuspendingMessageDispatchInterceptor<QueryMessage<*, *>>> = CopyOnWriteArrayList()
        val resultInterceptors: List<SuspendingResultHandlerInterceptor<QueryMessage<*, *>, ResultMessage<*>>> = CopyOnWriteArrayList()

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         * specifications
         */
        fun validate() = apply {
            BuilderUtils.assertThat(this::queryBus.isInitialized, { it == true }, "The QueryBus is a hard requirement and should be provided")
        }
    }
}