package com.pdf.pdfreader.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdf.pdfreader.utiles.PdfCompressionEngine
import com.pdf.pdfreader.utiles.PdfMergeEngine
import com.pdf.pdfreader.utiles.PdfSplitEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

/** Result state shared by every PDF-tools operation, surfaced to the tools sheet. */
sealed interface ToolStatus {
    data object Idle : ToolStatus
    data class Running(val label: String) : ToolStatus
    data class Success(val message: String, val outputPath: String?) : ToolStatus
    data class Error(val message: String) : ToolStatus
}

/**
 * Thin UI driver over [PdfMergeEngine], [PdfSplitEngine] and [PdfCompressionEngine]. Holds no
 * editor state; each call runs the matching engine off the main thread and publishes a
 * [ToolStatus] for the sheet to render (progress / success summary / error).
 */
@HiltViewModel
class PdfToolsViewModel @Inject constructor(
    application: Application,
    private val mergeEngine: PdfMergeEngine,
    private val splitEngine: PdfSplitEngine,
    private val compressionEngine: PdfCompressionEngine
) : AndroidViewModel(application) {

    private val _status = MutableStateFlow<ToolStatus>(ToolStatus.Idle)
    val status = _status.asStateFlow()

    fun resetStatus() { _status.value = ToolStatus.Idle }

    fun compress(path: String, level: PdfCompressionEngine.Level) = run("Compressing…") {
        val r = compressionEngine.compress(path, level)
        when {
            r == null -> ToolStatus.Error("Compression failed")
            r.savedFraction <= 0f -> ToolStatus.Success(
                "Already optimized — no large images to shrink", r.outputPath
            )
            else -> ToolStatus.Success(
                "${percent(r.savedFraction)} smaller · ${human(r.originalBytes)} → ${human(r.compressedBytes)}",
                r.outputPath
            )
        }
    }

    fun splitEveryPage(path: String) = run("Splitting…") {
        val outs = splitEngine.splitEveryPage(path)
        if (outs.isEmpty()) ToolStatus.Error("Split failed")
        else ToolStatus.Success("Created ${outs.size} single-page files", outs.firstOrNull()?.outputPath)
    }

    fun splitRanges(path: String, ranges: List<IntRange>) = run("Splitting…") {
        if (ranges.isEmpty()) return@run ToolStatus.Error("Enter at least one valid range")
        val outs = splitEngine.splitRanges(path, ranges)
        if (outs.isEmpty()) ToolStatus.Error("No valid pages in the given ranges")
        else ToolStatus.Success("Created ${outs.size} file(s)", outs.firstOrNull()?.outputPath)
    }

    fun merge(currentPath: String, extraUris: List<Uri>) = run("Merging…") {
        val cached = copyUrisToCache(extraUris)
        try {
            if (cached.isEmpty()) return@run ToolStatus.Error("No PDFs selected to merge")
            val inputs = listOf(currentPath) + cached
            val r = mergeEngine.merge(inputs)
            if (r == null) ToolStatus.Error("Merge failed")
            else ToolStatus.Success("Merged ${inputs.size} files · ${r.pageCount} pages", r.outputPath)
        } finally {
            cached.forEach { runCatching { File(it).delete() } }
        }
    }

    private fun run(label: String, block: suspend () -> ToolStatus) {
        _status.value = ToolStatus.Running(label)
        viewModelScope.launch {
            _status.value = try {
                block()
            } catch (e: Exception) {
                ToolStatus.Error(e.message ?: "Operation failed")
            }
        }
    }

    /** Copy picker-provided content URIs into cache as real files the engines can read. */
    private suspend fun copyUrisToCache(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        uris.mapNotNull { uri ->
            try {
                val out = File(ctx.cacheDir, "merge_${System.nanoTime()}.pdf")
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                if (out.length() > 0) out.absolutePath else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun percent(fraction: Float): String =
        String.format(Locale.US, "%.0f%%", fraction * 100)

    private fun human(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        return String.format(Locale.US, "%.1f MB", kb / 1024.0)
    }

    companion object {
        /** Parse "1-10, 12, 15-20" into inclusive 1-based ranges; invalid tokens are skipped. */
        fun parseRanges(input: String): List<IntRange> =
            input.split(',', ';')
                .mapNotNull { token ->
                    val t = token.trim()
                    if (t.isEmpty()) return@mapNotNull null
                    val dash = t.split('-').map { it.trim() }
                    when (dash.size) {
                        1 -> dash[0].toIntOrNull()?.let { it..it }
                        2 -> {
                            val a = dash[0].toIntOrNull(); val b = dash[1].toIntOrNull()
                            if (a != null && b != null && a <= b) a..b else null
                        }
                        else -> null
                    }
                }
    }
}
