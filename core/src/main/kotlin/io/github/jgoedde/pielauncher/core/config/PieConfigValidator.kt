package io.github.jgoedde.pielauncher.core.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One import error: [path] is a JSONPath into the file (`$.layers[0].items[2]`). */
data class ConfigError(
    val path: String,
    val message: String,
)

/**
 * Checks a parsed Pie Config against the structural rules of `spec/pie-config.schema.json` and
 * the semantic rules from its `$comment`, collecting every error instead of stopping at the first.
 * The schema itself is not read at runtime (ADR 0005); a JVM test keeps the two in line.
 */
internal class PieConfigValidator {
    private val errors = mutableListOf<ConfigError>()

    /** Valid Layer ids, with the path of each. */
    private val layerIds = mutableListOf<Pair<String, String>>()

    /** Layer Switches: (owning Layer id or null, target id, path). */
    private val switches = mutableListOf<Triple<String?, String, String>>()

    fun validate(root: JsonElement): List<ConfigError> {
        val obj = root.objectAt(ROOT) ?: return errors
        obj.onlyKeys(ROOT, setOf("\$schema", "version", "baseLayer", "layers"))
        obj.required(ROOT, "version", "baseLayer", "layers")
        obj["\$schema"]?.string(ROOT.key("\$schema"))
        obj["version"]?.let { version(it, ROOT.key("version")) }
        val baseLayer = obj["baseLayer"]?.let { layerId(it, ROOT.key("baseLayer")) }
        obj["layers"]?.arrayAt(ROOT.key("layers"), min = 1)?.forEachIndexed { i, layer -> layer(layer, ROOT.key("layers").index(i)) }

        val known = layerIds.map { it.first }.toSet()
        if (baseLayer != null && baseLayer !in known) error(ROOT.key("baseLayer"), "names no Layer: \"$baseLayer\"")
        layerIds
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
            .forEach { (id, paths) -> paths.drop(1).forEach { error(it, "duplicate Layer id \"$id\"") } }
        switches.forEach { (owner, target, path) ->
            when (target) {
                owner -> error(path, "Layer Switch targets its own Layer \"$target\"")
                !in known -> error(path, "Layer Switch targets an unknown Layer \"$target\"")
            }
        }
        return errors
    }

    private fun version(
        element: JsonElement,
        path: String,
    ) {
        val version = (element as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        when {
            version == null -> error(path, "must be an integer")
            version > PieConfigCodec.SUPPORTED_VERSION ->
                error(path, "version $version is higher than supported (${PieConfigCodec.SUPPORTED_VERSION})")
            version != PieConfigCodec.SUPPORTED_VERSION.toLong() -> error(path, "must be ${PieConfigCodec.SUPPORTED_VERSION}")
        }
    }

    private fun layer(
        element: JsonElement,
        path: String,
    ) {
        val obj = element.objectAt(path) ?: return
        obj.onlyKeys(path, setOf("id", "name", "color", "icon", "items"))
        obj.required(path, "id", "name", "items")
        val id = obj["id"]?.let { layerId(it, path.key("id")) }
        if (id != null) layerIds += id to path.key("id")
        obj["name"]?.string(path.key("name"), minLength = 1)
        obj["color"]?.string(path.key("color"))?.let {
            if (PieColor.parse(it) == null) error(path.key("color"), "must be #RRGGBB or one of primary, secondary, tertiary")
        }
        obj["icon"]?.string(path.key("icon"), pattern = ICON, patternText = "a Material Symbols name (a-z, 0-9, _)")
        obj["items"]?.arrayAt(path.key("items"), min = 1, max = MAX_ITEMS)?.forEachIndexed { i, item ->
            item(item, path.key("items").index(i), owner = id)
        }
    }

    private fun item(
        element: JsonElement,
        path: String,
        owner: String?,
    ) {
        val obj = element.objectAt(path) ?: return
        val type = obj["type"]
        if (type == null) {
            error(path, "missing required field \"type\"")
            return
        }
        when (type.string(path.key("type"))) {
            "app" -> {
                obj.onlyKeys(path, setOf("type", "package", "activity"))
                obj.required(path, "package")
                obj["package"]?.packageName(path.key("package"))
                obj["activity"]?.string(path.key("activity"), minLength = 1)
            }
            "layer" -> {
                obj.onlyKeys(path, setOf("type", "layer"))
                obj.required(path, "layer")
                obj["layer"]?.let { layerId(it, path.key("layer")) }?.let { switches += Triple(owner, it, path.key("layer")) }
            }
            "shortcut" -> {
                obj.onlyKeys(path, setOf("type", "package", "shortcutId"))
                obj.required(path, "package", "shortcutId")
                obj["package"]?.packageName(path.key("package"))
                obj["shortcutId"]?.string(path.key("shortcutId"), minLength = 1)
            }
            null -> Unit
            else -> error(path.key("type"), "must be one of app, layer, shortcut")
        }
    }

    private fun layerId(
        element: JsonElement,
        path: String,
    ): String? = element.string(path, minLength = 1, pattern = LAYER_ID, patternText = "a Layer id (a-z, 0-9, -)")

    private fun JsonElement.packageName(path: String) = string(path, pattern = PACKAGE, patternText = "a package name")

    /** The string value, or null after recording why it is not an acceptable one. */
    private fun JsonElement.string(
        path: String,
        minLength: Int = 0,
        pattern: Regex? = null,
        patternText: String = "",
    ): String? {
        val value = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
        when {
            value == null -> error(path, "must be a string")
            value.length < minLength -> error(path, "must not be empty")
            pattern != null && !pattern.matches(value) -> error(path, "must be $patternText, was \"$value\"")
            else -> return value
        }
        return null
    }

    private fun JsonElement.objectAt(path: String): JsonObject? =
        (this as? JsonObject).also { if (it == null) error(path, "must be an object") }

    private fun JsonElement.arrayAt(
        path: String,
        min: Int,
        max: Int = Int.MAX_VALUE,
    ): JsonArray? {
        val array = this as? JsonArray
        when {
            array == null -> error(path, "must be an array")
            array.size < min -> error(path, "must have at least $min entr${if (min == 1) "y" else "ies"}")
            array.size > max -> error(path, "must have at most $max entries, has ${array.size}")
        }
        // Entries are still checked when the count is off, so every error shows at once.
        return array
    }

    private fun JsonObject.onlyKeys(
        path: String,
        allowed: Set<String>,
    ) = keys.filter { it !in allowed }.forEach { error(path.key(it), "unknown field") }

    private fun JsonObject.required(
        path: String,
        vararg keys: String,
    ) = keys.filter { it !in this }.forEach { error(path, "missing required field \"$it\"") }

    private fun error(
        path: String,
        message: String,
    ) {
        errors += ConfigError(path, message)
    }

    private companion object {
        const val ROOT = "$"
        const val MAX_ITEMS = 12
        val LAYER_ID = Regex("^[a-z0-9-]+$")
        val ICON = Regex("^[a-z0-9_]+$")
        val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        val PLAIN_KEY = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

        fun String.key(key: String) = if (PLAIN_KEY.matches(key)) "$this.$key" else "$this['$key']"

        fun String.index(i: Int) = "$this[$i]"
    }
}
