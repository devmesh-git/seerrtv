package ca.devmesh.seerrtv.model

import kotlinx.serialization.*
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@OptIn(ExperimentalSerializationApi::class)
object SafeIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = Int.serializer().nullable.descriptor

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeInt(value)
        }
    }

    override fun deserialize(decoder: Decoder): Int? {
        val input = decoder as? JsonDecoder ?: return try {
            decoder.decodeInt()
        } catch (_: Exception) {
            null
        }
        val element = input.decodeJsonElement()
        if (element is JsonPrimitive) {
            if (element.isString) {
                return element.content.toIntOrNull()
            }
            return element.intOrNull
        }
        return null
    }
}

@Serializable
data class UserResponse(
    val pageInfo: PageInfo,
    val results: List<User>
)

@Serializable
data class User(
    val id: Int,
    val email: String? = null,
    @Serializable(with = SafeIntSerializer::class)
    val permissions: Int? = null,
    val warnings: List<String> = emptyList(),
    val jellyfinUsername: String? = null,
    val username: String? = null,
    val recoveryLinkExpirationDate: String? = null,
    @Serializable(with = SafeIntSerializer::class)
    val userType: Int? = null,
    @Serializable(with = SafeIntSerializer::class)
    val plexId: Int? = null,
    val jellyfinUserId: String? = null,
    val plexToken: String? = null,
    val avatar: String? = null,
    @Serializable(with = SafeIntSerializer::class)
    val movieQuotaLimit: Int? = null,
    @Serializable(with = SafeIntSerializer::class)
    val movieQuotaDays: Int? = null,
    @Serializable(with = SafeIntSerializer::class)
    val tvQuotaLimit: Int? = null,
    @Serializable(with = SafeIntSerializer::class)
    val tvQuotaDays: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    @Serializable(with = SafeIntSerializer::class)
    val requestCount: Int? = null,
    val displayName: String = ""
)
