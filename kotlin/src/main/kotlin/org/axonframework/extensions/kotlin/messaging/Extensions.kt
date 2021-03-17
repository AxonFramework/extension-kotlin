package org.axonframework.extensions.kotlin.messaging

import org.axonframework.messaging.Message
import org.axonframework.messaging.ResultMessage
import org.axonframework.messaging.responsetypes.ResponseType
import org.axonframework.messaging.responsetypes.ResponseTypes
import java.util.*

@Suppress("UNCHECKED_CAST")
internal fun <R> ResultMessage<*>.payloadOrThrowException(): R {
    if (isExceptional) {
        throw exceptionResult()
    } else {
        return payload as R
    }
}


inline fun <reified R> acceptOneOf(): ResponseType<R> {
    return ResponseTypes.instanceOf(R::class.java)
}

inline fun <reified R> acceptManyOf(): ResponseType<List<R>> {
    return ResponseTypes.multipleInstancesOf(R::class.java)
}

inline fun <reified R> acceptOptionalOf(): ResponseType<Optional<R>> {
    return ResponseTypes.optionalInstanceOf(R::class.java)
}


@Suppress("UNCHECKED_CAST")
fun <T: Message<*>> T.withMetaData(metaData: Pair<String, *>) = withMetaData(mapOf(metaData)) as T

@Suppress("UNCHECKED_CAST")
fun <T: Message<*>> T.andMetaData(additionalMetaData: Pair<String, *>): T = andMetaData(mapOf(additionalMetaData)) as T
