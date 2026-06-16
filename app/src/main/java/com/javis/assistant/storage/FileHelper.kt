package com.javis.assistant.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

object FileHelper {

    fun browseDownloads(context: Context) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Browse Files").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun openFile(context: Context, file: File) {
        val mimeType = getMimeType(file.extension) ?: "*/*"
        val uri: Uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: IllegalArgumentException) {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun searchFiles(query: String, root: File = Environment.getExternalStorageDirectory()): List<File> {
        val results = mutableListOf<File>()
        try {
            root.walkTopDown()
                .onEnter { dir -> !dir.name.startsWith(".") && dir.canRead() }
                .filter { it.isFile && it.name.contains(query, ignoreCase = true) }
                .take(20)
                .forEach { results.add(it) }
        } catch (e: Exception) {
            // Permission denied or inaccessible
        }
        return results
    }

    private fun getMimeType(extension: String): String? {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    }
}
