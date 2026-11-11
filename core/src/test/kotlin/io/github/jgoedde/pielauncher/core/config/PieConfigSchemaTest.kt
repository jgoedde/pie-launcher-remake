package io.github.jgoedde.pielauncher.core.config

import io.github.optimumcode.json.schema.ErrorCollector
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.ValidationError
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps `spec/pie-config.schema.json` and [PieConfigCodec] in line (ADR 0005): every example and
 * fixture must get the same verdict from the schema as from the codec, except the semantic rules
 * the schema cannot express.
 */
class PieConfigSchemaTest {
    private val specDir = File(System.getProperty("pie.specDir"))
    private val schema = JsonSchema.fromDefinition(File(specDir, "pie-config.schema.json").readText())

    private fun fixtures(dir: String): List<File> {
        val files = File(javaClass.getResource("/pie-config/$dir")!!.toURI()).listFiles { f -> f.extension == "json" }!!.sorted()
        assertTrue("no fixtures in $dir", files.isNotEmpty())
        return files
    }

    private fun schemaErrors(file: File): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        schema.validate(Json.parseToJsonElement(file.readText()), ErrorCollector { errors += it })
        return errors
    }

    private fun decodes(file: File) = PieConfigCodec.decode(file.readText()) is PieConfigDecoding.Decoded

    @Test
    fun `the spec example and the valid fixtures pass the schema and decode`() {
        (listOf(File(specDir, "pie-config.example.json")) + fixtures("valid")).forEach { file ->
            assertTrue("${file.name}: ${schemaErrors(file)}", schemaErrors(file).isEmpty())
            assertTrue("${file.name} does not decode", decodes(file))
        }
    }

    @Test
    fun `fixtures breaking the schema are rejected by both`() {
        fixtures("schema-invalid").forEach { file ->
            assertTrue("${file.name} passes the schema", schemaErrors(file).isNotEmpty())
            assertFalse("${file.name} decodes", decodes(file))
        }
    }

    @Test
    fun `fixtures breaking only semantic rules pass the schema but are rejected by the codec`() {
        fixtures("semantic-invalid").forEach { file ->
            assertTrue("${file.name}: ${schemaErrors(file)}", schemaErrors(file).isEmpty())
            assertFalse("${file.name} decodes", decodes(file))
        }
    }
}
