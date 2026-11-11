package io.github.jgoedde.pielauncher.core.icons

import java.security.MessageDigest

/**
 * Identifies one cached Themed Icon mask.
 *
 * [item] tells icons of one package apart (an activity, or a Shortcut id and user); [version]
 * changes whenever the icon may have changed (the app's `versionCode`, a Shortcut's last change).
 */
data class MaskKey(
    val packageName: String,
    val item: String,
    val version: String,
    val sizePx: Int,
    val algorithmVersion: Int = MASK_ALGORITHM_VERSION,
) {
    /** The mask's file name: unique per key, and starting with the package so it can be invalidated. */
    val fileName: String
        get() {
            val digest = MessageDigest.getInstance("SHA-256").digest("$item\n$version\n$sizePx\n$algorithmVersion".toByteArray())
            return "$packageName$SEPARATOR${digest.joinToString("") { "%02x".format(it) }.take(HASH_CHARS)}$EXTENSION"
        }

    private companion object {
        const val HASH_CHARS = 32
    }
}

/** Package names never contain it, so it ends the package part of a file name. */
private const val SEPARATOR = '-'
private const val EXTENSION = ".a8"

/** Whether the mask file [fileName] belongs to [packageName]. */
fun isMaskOf(
    fileName: String,
    packageName: String,
): Boolean = fileName.startsWith("$packageName$SEPARATOR") && fileName.endsWith(EXTENSION)
