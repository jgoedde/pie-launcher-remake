package io.github.jgoedde.pielauncher.core.config

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface PieConfigDecoding {
    data class Decoded(
        val config: PieConfig,
    ) : PieConfigDecoding

    /** Every error found, each with its JSON path. */
    data class Rejected(
        val errors: List<ConfigError>,
    ) : PieConfigDecoding
}

/**
 * Decodes and encodes the Pie Config file. Decoding is strict: unknown fields are errors (`$schema`
 * is allowed) and a `version` higher than [SUPPORTED_VERSION] is rejected. All errors are reported
 * at once.
 */
object PieConfigCodec {
    const val SUPPORTED_VERSION = 1

    private val json =
        Json {
            classDiscriminator = "type"
            prettyPrint = true
            explicitNulls = false
        }

    fun decode(text: String): PieConfigDecoding {
        val root =
            try {
                json.parseToJsonElement(text)
            } catch (e: SerializationException) {
                return PieConfigDecoding.Rejected(listOf(ConfigError("$", "not valid JSON: ${e.message}")))
            }
        val errors = PieConfigValidator().validate(root)
        if (errors.isNotEmpty()) return PieConfigDecoding.Rejected(errors)
        return PieConfigDecoding.Decoded(json.decodeFromJsonElement(PieConfig.serializer(), root))
    }

    /** The normalized form: pretty-printed, same content. */
    fun encode(config: PieConfig): String = json.encodeToString(PieConfig.serializer(), config)
}
