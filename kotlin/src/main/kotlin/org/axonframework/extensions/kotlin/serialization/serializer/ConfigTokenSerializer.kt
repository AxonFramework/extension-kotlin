package org.axonframework.extensions.kotlin.serialization.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.axonframework.eventhandling.tokenstore.ConfigToken

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = ConfigToken::class)
class ConfigTokenSerializer : KSerializer<ConfigToken> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ConfigToken") {
        element<Map<String, String>>("config")
    }

    override fun serialize(encoder: Encoder, value: ConfigToken) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, MapSerializer(String.serializer(), String.serializer()), value.config)
        }
    }

    override fun deserialize(decoder: Decoder): ConfigToken {
        return decoder.decodeStructure(descriptor) {
            var config: Map<String, String>? = null

            loop@ while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    DECODE_DONE -> break@loop

                    0 -> config = decodeSerializableElement(descriptor, 0, MapSerializer(String.serializer(), String.serializer()))

                    else -> throw SerializationException("Unexpected index $index")
                }
            }

            ConfigToken(config)
        }
    }
}