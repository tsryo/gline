package com.gline

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** Hands a finished GPX to Beeline, the share sheet, or the Downloads folder. */
object GpxFiles {
    private const val GPX_MIME = "application/gpx+xml"

    // Types GPX files commonly arrive as; Beeline's file filter may list any of them.
    private val VIEW_MIMES = listOf(GPX_MIME, "application/octet-stream", "application/xml", "text/xml")

    /** Opens the GPX straight in Beeline if it's installed, else offers every app that opens GPX. */
    fun openInBeeline(context: Context, conversion: Conversion): String? {
        val uri = cacheUri(context, conversion)
        val beeline = VIEW_MIMES.firstNotNullOfOrNull { mime ->
            val intent = viewIntent(uri, mime)
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .firstOrNull { it.activityInfo.packageName.contains("beeline", ignoreCase = true) }
                ?.let { intent.setClassName(it.activityInfo.packageName, it.activityInfo.name) }
        }
        return try {
            context.startActivity(beeline ?: Intent.createChooser(viewIntent(uri, GPX_MIME), "Open GPX with"))
            if (beeline == null) "Beeline wasn't found as a GPX app, so choose it from the list (or use Save)." else null
        } catch (_: ActivityNotFoundException) {
            "No app on this phone opens GPX files. Use Save, then import it from Beeline."
        }
    }

    fun share(context: Context, conversion: Conversion) {
        val uri = cacheUri(context, conversion)
        val send = Intent(Intent.ACTION_SEND)
            .setType(GPX_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, conversion.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share GPX"))
    }

    /** Writes to Downloads/GLine, where Beeline's own "Import GPX route" picker can find it. */
    fun saveToDownloads(context: Context, conversion: Conversion): String {
        val resolver = context.contentResolver
        val folder = "${Environment.DIRECTORY_DOWNLOADS}/GLine"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, conversion.fileName)
            put(MediaStore.Downloads.MIME_TYPE, GPX_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, folder)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create the file in Downloads")
        resolver.openOutputStream(uri)?.use { it.write(conversion.gpx.toByteArray()) }
            ?: throw IOException("Couldn't write the file in Downloads")
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return "Saved to $folder/${conversion.fileName}"
    }

    private fun viewIntent(uri: Uri, mime: String) =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** The GPX as a file other apps can read through our FileProvider. */
    private fun cacheUri(context: Context, conversion: Conversion): Uri {
        val dir = File(context.cacheDir, "gpx").apply { mkdirs() }
        dir.listFiles()?.filter { it.name != conversion.fileName }?.forEach { it.delete() }
        val file = File(dir, conversion.fileName)
        file.writeText(conversion.gpx)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
