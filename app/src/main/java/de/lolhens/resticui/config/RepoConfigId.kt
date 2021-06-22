package de.lolhens.resticui.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

data class RepoConfigId(val id: UUID)

object RepoConfigIdSerializer : KSerializer<RepoConfigId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor((RepoConfigId::class).simpleName!!, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RepoConfigId) =
        encoder.encodeString(value.id.toString())

    override fun deserialize(decoder: Decoder): RepoConfigId =
        RepoConfigId(UUID.fromString(decoder.decodeString()))
}