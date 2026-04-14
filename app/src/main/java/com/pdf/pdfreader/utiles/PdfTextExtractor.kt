package com.pdf.pdfreader.utiles

import android.util.Log
import com.pdf.pdfreader.data.local.PdfDao
import com.pdf.pdfreader.data.local.PdfTextSnippet
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfTextExtractor @Inject constructor(
    private val pdfDao: PdfDao
) {
    suspend fun extractAndIndexPdf(path: String) = withContext(Dispatchers.IO.limitedParallelism(1)) {
        try {
            val file = File(path)
            if (!file.exists()) return@withContext
            
            val snippets = mutableListOf<PdfTextSnippet>()
            PDDocument.load(file).use { document ->
                if (document.isEncrypted) return@use
                
                val stripper = PDFTextStripper()
                for (i in 1..document.numberOfPages) {
                    stripper.startPage = i
                    stripper.endPage = i
                    val text = stripper.getText(document).trim()
                    if (text.isNotEmpty()) {
                        snippets.add(
                            PdfTextSnippet(
                                pdfPath = path,
                                pageIndex = i - 1,
                                textContent = text
                            )
                        )
                    }
                }
            }
            if (snippets.isNotEmpty()) {
                pdfDao.deleteTextSnippetsByPath(path)
                pdfDao.insertTextSnippets(snippets)
            }
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "Failed to extract text from $path", e)
        }
    }
}
