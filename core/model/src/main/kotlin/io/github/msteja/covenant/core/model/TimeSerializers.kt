package io.github.msteja.covenant.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** java.time values stored as their ISO-8601 text, so saved state stays readable. */
abstract class IsoSerializer<T : Any>(name: String, private val parse: (String) -> T) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor("covenant.$name", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): T = parse(decoder.decodeString())
}

object InstantSerializer : IsoSerializer<Instant>("Instant", Instant::parse)
object LocalTimeSerializer : IsoSerializer<LocalTime>("LocalTime", LocalTime::parse)
object LocalDateSerializer : IsoSerializer<LocalDate>("LocalDate", LocalDate::parse)
object DurationSerializer : IsoSerializer<Duration>("Duration", Duration::parse)
