package org.axonframework.extensions.kotlin.queryhandling.gateway

import kotlinx.coroutines.flow.*
import org.axonframework.extensions.kotlin.messaging.SuspendingMessageDispatchInterceptorSupport
import org.axonframework.extensions.kotlin.messaging.SuspendingResultHandlerInterceptorSupport
import org.axonframework.extensions.kotlin.messaging.acceptOneOf
import org.axonframework.messaging.ResultMessage
import org.axonframework.messaging.responsetypes.ResponseType
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryMessage
import org.axonframework.queryhandling.SubscriptionQueryBackpressure
import reactor.util.concurrent.Queues
import java.time.Duration

/**
 * Variation of the [QueryGateway], wrapping a [QueryBus] for a friendlier API.
 * Provides support for coroutines.
 *
 * @author Joel Feinstein
 * @since 0.2.0
 */
interface SuspendingQueryGateway : SuspendingMessageDispatchInterceptorSupport<QueryMessage<*, *>>,
    SuspendingResultHandlerInterceptorSupport<QueryMessage<*, *>, ResultMessage<*>> {

    /**
     * Sends the given `query` over the [QueryBus], expecting a response in the form of `responseType`
     * from a single source. The query name will be derived from the provided `query`. Execution may be
     * asynchronous, depending on the `QueryBus` implementation.
     *
     * @param query        The `query` to be sent
     * @param responseType The [ResponseType] used for this query
     * @param <R>          The response class contained in the given `responseType`
     * @param <Q>          The query class
     * @return The query result as dictated by the given `responseType`
     */
    suspend fun <R, Q> query(query: Q, responseType: ResponseType<R>): R {
        return query(QueryMessage.queryName(query), query, responseType)
    }

    /**
     * Sends the given `query` over the [QueryBus], expecting a response in the form of `responseType`
     * from a single source. Execution may be asynchronous, depending on the `QueryBus` implementation.
     *
     * @param queryName    A [String] describing the query to be executed
     * @param query        The `query` to be sent
     * @param responseType The [ResponseType] used for this query
     * @param <R>          The response class contained in the given `responseType`
     * @param <Q>          The query class
     * @return The query result as dictated by the given `responseType`
     */
    suspend fun <R, Q> query(queryName: String, query: Q, responseType: ResponseType<R>): R

    /**
     * Sends the given `query` over the [QueryBus], expecting a response in the form of `responseType`
     * from several sources. The returned [Flow] is completed when a `timeout` occurs or when all possible
     * results are received. The query name will be derived from the provided `query`. Execution may be
     * asynchronous, depending on the `QueryBus` implementation.
     *
     * @param query        The `query` to be sent
     * @param responseType The [ResponseType] used for this query
     * @param timeout      A timeout of `long` for the query
     * @param <R>          The response class contained in the given `responseType`
     * @param <Q>          The query class
     * @return A [Flow] containing the query results as dictated by the given `responseType`
     */
    suspend fun <R, Q> scatterGather(query: Q, responseType: ResponseType<R>, timeout: Duration): Flow<R> {
        return scatterGather(QueryMessage.queryName(query), query, responseType, timeout)
    }

    /**
     * Sends the given `query` over the [QueryBus], expecting a response in the form of `responseType`
     * from several sources. The returned [Flow] is completed when a `timeout` occurs or when all results
     * are received. Execution may be asynchronous, depending on the `QueryBus` implementation.
     *
     * @param queryName    A [String] describing the query to be executed
     * @param query        The `query` to be sent
     * @param responseType The [ResponseType] used for this query
     * @param timeout      A timeout of `long` for the query
     * @param <R>          The response class contained in the given `responseType`
     * @param <Q>          The query class
     * @return A [Flow] containing the query results as dictated by the given `responseType`
     */
    suspend fun <R, Q> scatterGather(queryName: String, query: Q, responseType: ResponseType<R>, timeout: Duration): Flow<R>

    /**
     * Sends the given `query` over the [QueryBus], returning the initial result and a stream of incremental
     * updates, received at the moment the query is sent, until it is cancelled by the caller or closed by the emitting
     * side. Should be used when the response type of the initial result and incremental update match.
     *
     * @param query      The `query` to be sent
     * @param resultType The response type used for this query
     * @param <Q>        The type of the query
     * @param <R>        The type of the result (initial & updates)
     * @return A [Flow] which can be used to cancel receiving updates
     *
     * @see QueryBus.subscriptionQuery
     */
    suspend fun <Q, R> subscriptionQuery(query: Q, resultType: ResponseType<R>): Flow<R> {
        val delegate = subscriptionQuery(query, resultType, resultType)
        return flowOf(delegate.initialResult()).onCompletion { emitAll(delegate.updates()) }
    }

    /**
     * Sends the given `query` over the [QueryBus], streaming incremental updates, received at the moment
     * the query is sent, until it is cancelled by the caller or closed by the emitting side.
     * Should be used when the subscriber is interested only in updates.
     *
     * @param query      The `query` to be sent
     * @param resultType The response type used for this query
     * @param <Q>        The type of the query
     * @param <R>        The type of the result (updates)
     * @return A [Flow] which can be used to cancel receiving updates
     *
     * @see QueryBus.subscriptionQuery
     */
    suspend fun <Q, R> queryUpdates(query: Q, resultType: ResponseType<R>): Flow<R> {
        val delegate = subscriptionQuery(query, ResponseTypes.instanceOf(Unit::class.java), resultType)
        return delegate.updates().onCompletion { delegate.close() }
    }

    /**
     * Sends the given `query` over the [QueryBus] and returns result containing initial response and
     * incremental updates, received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side.
     *
     * @param query               The `query` to be sent
     * @param initialResponseType The initial response type used for this query
     * @param updateResponseType  The update response type used for this query
     * @param <Q>                 The type of the query
     * @param <I>                 The type of the initial response
     * @param <U>                 The type of the incremental update
     * @return registration which can be used to cancel receiving updates
     *
     * @see QueryBus.subscriptionQuery
     */
    suspend fun <Q, I, U> subscriptionQuery(
        query: Q,
        initialResponseType: ResponseType<I>,
        updateResponseType: ResponseType<U>
    ): SuspendingSubscriptionQueryResult<I, U> {
        return subscriptionQuery(
            QueryMessage.queryName(query),
            query,
            initialResponseType,
            updateResponseType,
            SubscriptionQueryBackpressure.defaultBackpressure()
        )
    }

    /**
     * Sends the given `query` over the [QueryBus] and returns result containing initial response and
     * incremental updates, received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side.
     *
     * @param queryName           A [String] describing query to be executed
     * @param query               The `query` to be sent
     * @param initialResponseType The initial response type used for this query
     * @param updateResponseType  The update response type used for this query
     * @param backpressure        The backpressure mechanism to deal with producing of incremental updates
     * @param <Q>                 The type of the query
     * @param <I>                 The type of the initial response
     * @param <U>                 The type of the incremental update
     * @return registration which can be used to cancel receiving updates
     *
     * @see QueryBus.subscriptionQuery
     */
    suspend fun <Q, I, U> subscriptionQuery(
        queryName: String,
        query: Q,
        initialResponseType: ResponseType<I>,
        updateResponseType: ResponseType<U>,
        backpressure: SubscriptionQueryBackpressure
    ): SuspendingSubscriptionQueryResult<I, U> {
        return subscriptionQuery(
            queryName,
            query,
            initialResponseType,
            updateResponseType,
            backpressure,
            Queues.SMALL_BUFFER_SIZE
        )
    }

    /**
     * Sends the given `query` over the [QueryBus] and returns result containing initial response and
     * incremental updates, received at the moment the query is sent, until it is cancelled by the caller or closed by
     * the emitting side.
     *
     * @param queryName           A [String] describing query to be executed
     * @param query               The `query` to be sent
     * @param initialResponseType The initial response type used for this query
     * @param updateResponseType  The update response type used for this query
     * @param backpressure        The backpressure mechanism to deal with producing of incremental updates
     * @param updateBufferSize    The size of buffer which accumulates updates before subscription to the `flux`
     * is made
     * @param <Q>                 The type of the query
     * @param <I>                 The type of the initial response
     * @param <U>                 The type of the incremental update
     * @return registration which can be used to cancel receiving updates
     *
     * @see QueryBus.subscriptionQuery
     */
    suspend fun <Q, I, U> subscriptionQuery(
        queryName: String, query: Q,
        initialResponseType: ResponseType<I>,
        updateResponseType: ResponseType<U>,
        backpressure: SubscriptionQueryBackpressure,
        updateBufferSize: Int
    ): SuspendingSubscriptionQueryResult<I, U>
}

/**
 * Sends the given `query` over the [QueryBus], expecting a response in the form of `responseType`
 * from a single source. Execution may be asynchronous, depending on the `QueryBus` implementation.
 *
 * @param queryName    A [String] describing the query to be executed
 * @param query        The `query` to be sent
 * @param responseType The [ResponseType] used for this query
 * @param <R>          The response class contained in the given `responseType`
 * @param <Q>          The query class
 * @return The query result as dictated by the given `responseType`
 */
suspend inline fun <reified R> SuspendingQueryGateway.query(query: Any?): R {
    return query(query, acceptOneOf())
}

/**
 * Sends the given `query` over the [QueryBus], expecting a response in the form of `responseType`
 * from several sources. The returned [Flow] is completed when a `timeout` occurs or when all possible
 * results are received. The query name will be derived from the provided `query`. Execution may be
 * asynchronous, depending on the `QueryBus` implementation.
 *
 * @param query        The `query` to be sent
 * @param timeout      A timeout of `long` for the query
 * @param <R>          The response class contained in the given `responseType`
 * @param <Q>          The query class
 * @return A [Flow] containing the query results as dictated by the given `responseType`
 */
suspend inline fun <reified R> SuspendingQueryGateway.scatterGather(query: Any?, timeout: Duration): Flow<R> {
    return scatterGather(query, acceptOneOf(), timeout)
}

/**
 * Sends the given `query` over the [QueryBus] and returns result containing initial response and
 * incremental updates, received at the moment the query is sent, until it is cancelled by the caller or closed by
 * the emitting side.
 *
 * @param query               The `query` to be sent
 * @param <Q>                 The type of the query
 * @param <I>                 The type of the initial response
 * @param <U>                 The type of the incremental update
 * @return registration which can be used to cancel receiving updates
 *
 * @see QueryBus.subscriptionQuery
 */
suspend inline fun <reified I, reified U> SuspendingQueryGateway.subscriptionQuery(query: Any?): SuspendingSubscriptionQueryResult<I, U> {
    return subscriptionQuery(query, acceptOneOf(), acceptOneOf())
}

/**
 * Sends the given `query` over the [QueryBus], streaming incremental updates, received at the moment
 * the query is sent, until it is cancelled by the caller or closed by the emitting side.
 * Should be used when the subscriber is interested only in updates.
 *
 * @param query      The `query` to be sent
 * @param <Q>        The type of the query
 * @param <R>        The type of the result (updates)
 * @return A [Flow] which can be used to cancel receiving updates
 *
 * @see QueryBus.subscriptionQuery
 */
suspend inline fun <reified R> SuspendingQueryGateway.queryUpdates(query: Any?): Flow<R> {
    return queryUpdates(query, acceptOneOf())
}