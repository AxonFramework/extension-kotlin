package org.axonframework.extensions.kotlin.serialization.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.axonframework.eventhandling.tokenstore.ConfigToken

@Serializable
data class ConfigTokenSurrogate(
    private val config: Map<String, String>
) {
    fun toToken() = ConfigToken(config)
}

fun ConfigToken.toSurrogate() = ConfigTokenSurrogate(config)

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = ConfigToken::class)
class ConfigTokenSerializer : KSerializer<ConfigToken> {
    override val descriptor: SerialDescriptor = ConfigTokenSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ConfigToken) =
        encoder.encodeSerializableValue(ConfigTokenSurrogate.serializer(), value.toSurrogate())

    override fun deserialize(decoder: Decoder): ConfigToken =
        decoder.decodeSerializableValue(ConfigTokenSurrogate.serializer()).toToken()
}

// The following does not work, emits compiler errors.
// Also with the `-Xuse-ir` Kotlin compiler argument (version 1.6.20)

//@OptIn(ExperimentalSerializationApi::class)
//@Serializer(forClass = ConfigToken::class)
//object ConfigTokenSerializer