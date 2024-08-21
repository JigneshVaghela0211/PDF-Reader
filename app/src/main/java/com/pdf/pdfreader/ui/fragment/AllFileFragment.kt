package com.pdf.pdfreader.ui.fragment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.pdf.pdfreader.BuildConfig
import com.pdf.pdfreader.base.BaseFragment
import com.pdf.pdfreader.databinding.AllFileFragmentBinding
import com.pdf.pdfreader.extension.hasAllFilesPermission
import com.pdf.pdfreader.ui.adapter.PDFAdapter
import com.pdf.pdfreader.utiles.PdfFileDetails
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@AndroidEntryPoint
class AllFileFragment : BaseFragment<AllFileFragmentBinding>() {
    private var job: Job? = null
    private val list = ArrayList<PdfFileDetails>()
    private val pdfAdapter by lazy {
        PDFAdapter(list)
    }

    override fun createViewBinding() = AllFileFragmentBinding.inflate(layoutInflater)


    @RequiresApi(Build.VERSION_CODES.R)
    override fun bindData() {
        setRecyclerview()
        if (hasAllFilesPermission()) {
            loadPdfFiles()
        } else {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                )
            )
        }
    }


    private fun setRecyclerview() = with(binding) {
        recyclerViewPDF.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewPDF.adapter = pdfAdapter
    }

    private fun getPdfFileDetails(context: Context): List<PdfFileDetails> {
        val pdfList = mutableListOf<PdfFileDetails>()
        val uri = MediaStore.Files.getContentUri("external")

        val selection = MediaStore.Files.FileColumns.MIME_TYPE + " = ?"
        val selectionArgs = arrayOf("application/pdf")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.IS_TRASHED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.PARENT
        )

        val cursor = context.contentResolver.query(
            uri, projection, selection, selectionArgs, null
        )

        cursor?.use {
            while (cursor.moveToNext()) {
                val filePath =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))

                val size =
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))

                val fileName =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))

                val dateAdded =
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)) * 1000

                val lastModified =
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)) * 1000 // Convert to milliseconds

                val type =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE))

                val trashed =
                    cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.IS_TRASHED))

                val relativePath =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH))

                val parent =
                    cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.PARENT))



                pdfList.add(
                    PdfFileDetails(
                        filePath = filePath,
                        fileName = fileName,
                        lastModified = lastModified,
                        size = size,
                        type = type,
                        parent = parent,
                        relativePath = relativePath, addedDate = dateAdded, trashed = trashed
                    )
                )
            }
        }
        return pdfList
    }

    private fun loadPdfFiles() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            val pdfFiles = withContext(Dispatchers.IO) {
                getPdfFileDetails(requireContext())
            }
            list.clear()
            list.addAll(pdfFiles)
            pdfAdapter.notifyItemRangeChanged(0, list.size)

        }
    }
}