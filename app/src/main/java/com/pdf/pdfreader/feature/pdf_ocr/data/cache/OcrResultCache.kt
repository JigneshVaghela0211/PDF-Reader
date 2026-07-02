package com.pdf.pdfreader.feature.pdf_ocr.data.cache

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrPage
import com.pdf.pdfreader.feature.pdf_ocr.domain.model.OcrScript
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-page OCR result cache: `filesDir/ocr_cache/<docKey>/<script>/page_<i>.json`.
 *
 * - One JSON file per page IS the resume granularity: a cancelled run keeps its
 *   finished pages; the next run skips them via [cachedPageIndices].
 * - `docKey = sha256(path|size|lastModified)` — editing/replacing the source PDF
 *   changes the key, so stale results are never served; old dirs are pruned.
 * - Survives process death (files, not memory), no Room migration required.
 */
@Singleton
class OcrResultCache @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "OcrResultCache"
        private const val MAX_CACHED_DOCS = 20
    }

    private val gson = Gson()
    private val root: File get() = File(context.filesDir, "ocr_cache")

    suspend fun get(path: String, pageIndex: Int, script: OcrScript): OcrPage? =
        withContext(Dispatchers.IO) {
            val file = pageFile(path, script, pageIndex)
            if (!file.exists()) return@withContext null
            try {
                gson.fromJson(file.readText(), OcrPage::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "Corrupt cache entry ${file.name}; dropping", e)
                file.delete()
                null
            }
        }

    suspend fun put(path: String, page: OcrPage) = withContext(Dispatchers.IO) {
        try {
            val file = pageFile(path, page.script, page.pageIndex)
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(page))
            prune()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache page ${page.pageIndex} of $path", e)
        }
    }

    suspend fun cachedPageIndices(path: String, script: OcrScript): Set<Int> =
        withContext(Dispatchers.IO) {
            val dir = scriptDir(path, script)
            dir.listFiles()
                ?.mapNotNull { it.name.removeSurrounding("page_", ".json").toIntOrNull() }
                ?.toSet()
                ?: emptySet()
        }

    suspend fun invalidate(path: String) = withContext(Dispatchers.IO) {
        File(root, docKey(path)).deleteRecursively()
        Unit
    }

    /** Drop the oldest cached documents beyond [MAX_CACHED_DOCS]. */
    private fun prune() {
        val docs = root.listFiles()?.filter { it.isDirectory } ?: return
        if (docs.size <= MAX_CACHED_DOCS) return
        docs.sortedBy { it.lastModified() }
            .take(docs.size - MAX_CACHED_DOCS)
            .forEach { it.deleteRecursively() }
    }

    private fun scriptDir(path: String, script: OcrScript): File =
        File(File(root, docKey(path)), script.name)

    private fun pageFile(path: String, script: OcrScript, pageIndex: Int): File =
        File(scriptDir(path, script), "page_$pageIndex.json")

    /** Identity of the PDF's current content — changes whenever the file changes. */
    private fun docKey(path: String): String {
        val file = File(path)
        val identity = "$path|${file.length()}|${file.lastModified()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
