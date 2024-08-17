package com.pdf.pdfreader.ui.fragment

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.pdf.pdfreader.BuildConfig
import com.pdf.pdfreader.base.BaseFragment
import com.pdf.pdfreader.databinding.AllFileFragmentBinding
import com.pdf.pdfreader.extension.hasAllFilesPermission
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

    override fun createViewBinding() = AllFileFragmentBinding.inflate(layoutInflater)


    @RequiresApi(Build.VERSION_CODES.R)
    override fun bindData() {
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

    private fun getPdfFileDetails(context: Context): List<PdfFileDetails> {
        val pdfList = mutableListOf<PdfFileDetails>()
        val uri = MediaStore.Files.getContentUri("external")

        val selection = MediaStore.Files.FileColumns.MIME_TYPE + " = ?"
        val selectionArgs = arrayOf("application/pdf")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )

        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            while (cursor.moveToNext()) {
                val filePath =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
                val fileName =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                val lastModified =
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)) * 1000 // Convert to milliseconds
                val size =
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))

                pdfList.add(PdfFileDetails(filePath, fileName, lastModified, size))
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
        }
    }
}