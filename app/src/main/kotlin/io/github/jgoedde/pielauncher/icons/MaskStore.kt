package io.github.jgoedde.pielauncher.icons

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.createBitmap
import io.github.jgoedde.pielauncher.core.icons.MaskKey
import io.github.jgoedde.pielauncher.core.icons.isMaskOf
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Themed Icon masks on disk: one file per [MaskKey] holding the ALPHA_8 bitmap's raw bytes.
 * Lives in `cacheDir`, so the system may clear it at any time; a missing or odd file is regenerated.
 * Call from a background thread.
 */
internal class MaskStore(
    private val dir: File,
) {
    fun read(key: MaskKey): Bitmap? {
        val file = File(dir, key.fileName)
        if (!file.isFile) return null
        val bitmap = createBitmap(key.sizePx, key.sizePx, Bitmap.Config.ALPHA_8)
        return try {
            val bytes = file.readBytes()
            if (bytes.size != bitmap.byteCount) {
                bitmap.recycle()
                return null
            }
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
            bitmap
        } catch (e: IOException) {
            Log.w(TAG, "Could not read mask ${file.name}", e)
            bitmap.recycle()
            null
        }
    }

    fun write(
        key: MaskKey,
        mask: Bitmap,
    ) {
        val bytes = ByteBuffer.allocate(mask.byteCount).also { mask.copyPixelsToBuffer(it) }.array()
        try {
            dir.mkdirs()
            // Write aside and rename, so a reader never sees half a file.
            val temp = File.createTempFile(key.packageName, ".tmp", dir)
            temp.writeBytes(bytes)
            if (!temp.renameTo(File(dir, key.fileName))) temp.delete()
        } catch (e: IOException) {
            Log.w(TAG, "Could not write mask ${key.fileName}", e)
        }
    }

    /** Deletes every mask of [packageName]. */
    fun invalidate(packageName: String) {
        dir.listFiles { _, name -> isMaskOf(name, packageName) }?.forEach { it.delete() }
    }

    private companion object {
        const val TAG = "MaskStore"
    }
}
