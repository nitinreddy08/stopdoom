package app.unrot.inspector

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Getting recordings off the phone (SPEC §15.2): the system share sheet for one recording or a
 * zip of all, or a zip saved to Downloads. Only ever called after the user taps a button.
 */
object TraceSharing {
    private const val SHARED_DIR = "shared"
    private const val TEXT = "text/plain"
    private const val ZIP = "application/zip"
    private val stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** Shares the trace and its `.expected.json` together. */
    fun share(context: Context, trace: SavedTrace, subject: String, chooserTitle: String) {
        val uris = listOf(trace.trace, trace.expectation).filter {
            it.isFile
        }.map { uri(context, it) }
        if (uris.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = TEXT
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        startChooser(context, intent, uris, chooserTitle)
    }

    fun shareZip(context: Context, zip: File, subject: String, chooserTitle: String) {
        val uri = uri(context, zip)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = ZIP
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        startChooser(context, intent, listOf(uri), chooserTitle)
    }

    /** A fresh, empty location for a zip of all recordings; older shared zips are removed. */
    fun newZipFile(context: Context, nowMs: Long = System.currentTimeMillis()): File {
        val dir = File(context.cacheDir, SHARED_DIR)
        dir.listFiles()?.forEach { it.delete() }
        val time = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).format(stamp)
        return File(dir, "unrot-traces-$time.zip")
    }

    /** Copies [zip] to the public Downloads folder (no permission needed on Android 10+). */
    fun saveToDownloads(context: Context, zip: File): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, zip.name)
            put(MediaStore.MediaColumns.MIME_TYPE, ZIP)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("no output stream")
            output.use { out -> zip.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.traces", file)

    private fun startChooser(context: Context, intent: Intent, uris: List<Uri>, title: String) {
        // ClipData carries the read grant through the chooser to the receiving app.
        val clip = ClipData.newRawUri(null, uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, title)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
