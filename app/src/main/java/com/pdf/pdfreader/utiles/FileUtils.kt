package com.pdf.pdfreader.utiles

import android.annotation.TargetApi
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.DatabaseUtils
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.pdf.pdfreader.R
import java.io.*
import java.text.DecimalFormat
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/*
 * Copyright (C) 2007-2008 OpenIntents.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * @author Peli
 * @author paulburke (ipaulpro)
 * @version 2013-12-11
 */
object FileUtils {
    const val MIME_TYPE_AUDIO = "audio/*"
    const val MIME_TYPE_TEXT = "text/*"
    const val MIME_TYPE_IMAGE = "image/*"
    const val MIME_TYPE_VIDEO = "video/*"
    const val MIME_TYPE_APP = "application/*"
    const val HIDDEN_PREFIX = "."

    /**
     * TAG for log messages.
     */
    const val TAG = "FileUtils"
    private const val DEBUG = true // Set to true to enable logging

    /**
     * File and folder comparator. TODO Expose sorting option method
     *
     * @author paulburke
     */
    var sComparator =
        java.util.Comparator<File> { f1, f2 -> // Sort alphabetically by lower case, which is much cleaner
            f1.name.toLowerCase().compareTo(
                f2.name.toLowerCase()
            )
        }

    /**
     * File (not directories) filter.
     *
     * @author paulburke
     */
    var sFileFilter = FileFilter { file: File ->
        val fileName = file.name
        file.isFile && !fileName.startsWith(HIDDEN_PREFIX)
    }

    /**
     * Folder (directories) filter.
     *
     * @author paulburke
     */
    var sDirFilter = FileFilter { file: File ->
        val fileName = file.name
        file.isDirectory && !fileName.startsWith(HIDDEN_PREFIX)
    }
    private val cache: File? = null

    /**
     * Gets the extension of a file name, like ".png" or ".jpg".
     *
     * @param uri
     * @return Extension including the dot("."); "" if there is no extension;
     * null if uri was null.
     */
    fun getExtension(uri: String?): String? {
        if (uri == null) {
            return null
        }
        val dot = uri.lastIndexOf(".")
        return if (dot >= 0) {
            uri.substring(dot)
        } else {
            // No extension.
            ""
        }
    }

    /**
     * @return Whether the URI is a local one.
     */
    fun isLocal(url: String?): Boolean {
        return url != null && !url.startsWith("http://") && !url.startsWith("https://")
    }

    /**
     * @return True if Uri is a MediaStore Uri.
     * @author paulburke
     */
    fun isMediaUri(uri: Uri?): Boolean {
        return "media".equals(uri!!.authority, ignoreCase = true)
    }

    /**
     * Convert File into Uri.
     *
     * @param file
     * @return uri
     */
    fun getUri(file: File?): Uri? {
        return if (file != null) {
            Uri.fromFile(file)
        } else null
    }
    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is [LocalStorageProvider].
     * @author paulburke
     */
    /* public static boolean isLocalStorageDocument(Uri uri) {
        return LocalStorageProvider.AUTHORITY.equals(uri.getAuthority());
    }*/
    /**
     * Returns the path only (without file name).
     *
     * @param file
     * @return
     */
    fun getPathWithoutFilename(file: File?): File? {
        return if (file != null) {
            if (file.isDirectory) {
                // no file to be split off. Return everything
                file
            } else {
                val filename = file.name
                val filepath = file.absolutePath

                // Construct path without file name.
                var pathwithoutname = filepath.substring(
                    0,
                    filepath.length - filename.length
                )
                if (pathwithoutname.endsWith("/")) {
                    pathwithoutname = pathwithoutname.substring(0, pathwithoutname.length - 1)
                }
                File(pathwithoutname)
            }
        } else null
    }

    /**
     * @return The MIME type for the given file.
     */
    fun getMimeType(file: File): String? {
        val extension = getExtension(file.name)
        return if (extension!!.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            extension.substring(1)
        ) else "application/octet-stream"
    }

    /**
     * @return The MIME type for the give Uri.
     */
    fun getMimeType(context: Context, uri: Uri): String? {
        val file = File(getPath(context, uri))
        return getMimeType(file)
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     * @author paulburke
     */
    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     * @author paulburke
     */
    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     * @author paulburke
     */
    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     * @author paulburke
     */
    fun getDataColumn(
        context: Context, uri: Uri?, selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(
            column
        )
        try {
            cursor = context.contentResolver.query(
                uri!!, projection, selection, selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                if (DEBUG) DatabaseUtils.dumpCursor(cursor)
                val columnIndex = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(columnIndex)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.<br></br>
     * <br></br>
     * Callers should check whether the path is local before assuming it
     * represents a local file.
     *
     * @param context The context.
     * @param uri     The Uri to query.
     * @author paulburke
     * @see .isLocal
     * @see .getFile
     */
    fun getPath(context: Context, uri: Uri): String? {
        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // LocalStorageProvider
            /*if (isLocalStorageDocument(uri)) {
                // The path is the id
                return DocumentsContract.getDocumentId(uri);
            }*/
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
                )
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    "video" -> {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                    "audio" -> {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(
                    split[1]
                )
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        }
        // MediaStore (and general)
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            if (isGooglePhotosUri(uri)) return uri.lastPathSegment
            val cR = context.contentResolver
            val mime = MimeTypeMap.getSingleton()
            val type = mime.getExtensionFromMimeType(cR.getType(uri))
            var input: InputStream? = null
            var file: File? = null
            try {
                try {
                    input = context.contentResolver.openInputStream(uri)
                    //You can use this bitmap according to your purpose or Set bitmap to imageview
                    /* ----------- Support only to Android 10 --------------
                    String file_path = Environment.getExternalStorageDirectory().getAbsolutePath() +
                            "/" + context.getString(R.string.app_name) + "/" + type;
                    File dir = new File(file_path);
                    if (!dir.exists())
                        dir.mkdirs();
                    file = new File(dir, type + "Entity" + (int) System.currentTimeMillis() + "." + type);
                    --------------Support only to Android 10 ------------------*/

                    /* ------------------ Android 11 Supported ---------------*/
                    val storageDir = File(context.cacheDir, context.getString(R.string.app_name))
                    storageDir.mkdirs()
                    file = File(
                        storageDir,
                        type + "Entity" + System.currentTimeMillis().toInt() + "." + type
                    )
                    val output: OutputStream = FileOutputStream(file)
                    /* ------------------ Android 11 Supported ---------------*/try {
                        val buffer = ByteArray(4 * 1024) // or other buffer size
                        var read: Int
                        while (input!!.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    } finally {
                        output.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace() // handle exception, define IOException and others
                }
            } finally {
                try {
                    input?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            if (file != null) return file.absolutePath
            /* // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();
            try {
                InputStream is = context.getContentResolver().openInputStream(uri);
                if (is != null) {
                    Bitmap pictureBitmap = BitmapFactory.decodeStream(is);
                    //You can use this bitmap according to your purpose or Set bitmap to imageview
                    String file_path = Environment.getExternalStorageDirectory().getAbsolutePath() +
                            "/Images";
                    File dir = new File(file_path);
                    if (!dir.exists())
                        dir.mkdirs();
                    File file = new File(dir, "ImageEntity" + (int) System.currentTimeMillis() + ".png");
                    FileOutputStream fOut = new FileOutputStream(file);

                    pictureBitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
                    fOut.flush();
                    fOut.close();

                    return file.getAbsolutePath();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        } else if (uri.toString().startsWith("content://com.google.android.apps.photos.content")) {
            try {
                val `is` = context.contentResolver.openInputStream(uri)
                if (`is` != null) {
                    val pictureBitmap = BitmapFactory.decodeStream(`is`)
                    //You can use this bitmap according to your purpose or Set bitmap to imageview
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }
        return null
    }

    /**
     * Convert Uri into File, if possible.
     *
     * @return file A local file that the Uri was pointing to, or null if the
     * Uri is unsupported or pointed to a remote resource.
     * @author paulburke
     * @see .getPath
     */
    fun getFile(context: Context, uri: Uri?): File? {
        if (uri != null) {
            val path = getPath(context, uri)
            if (path != null && isLocal(path)) {
                return File(path)
            }
        }
        return null
    }

    /**
     * Get the file size in a human-readable string.
     *
     * @param size
     * @return
     * @author paulburke
     */
    fun getReadableFileSize(size: Int): String {
        val BYTES_IN_KILOBYTES = 1024
        val dec = DecimalFormat("###.#")
        val KILOBYTES = " KB"
        val MEGABYTES = " MB"
        val GIGABYTES = " GB"
        var fileSize = 0f
        var suffix = KILOBYTES
        if (size > BYTES_IN_KILOBYTES) {
            fileSize = (size / BYTES_IN_KILOBYTES).toFloat()
            if (fileSize > BYTES_IN_KILOBYTES) {
                fileSize /= BYTES_IN_KILOBYTES
                if (fileSize > BYTES_IN_KILOBYTES) {
                    fileSize /= BYTES_IN_KILOBYTES
                    suffix = GIGABYTES
                } else {
                    suffix = MEGABYTES
                }
            }
        }
        return dec.format(fileSize.toDouble()) + suffix
    }

    /**
     * Attempt to retrieve the thumbnail of given File from the MediaStore. This
     * should not be called on the UI thread.
     *
     * @param context
     * @param file
     * @return
     * @author paulburke
     */
    fun getThumbnail(context: Context, file: File): Bitmap? {
        return getThumbnail(context, getUri(file), getMimeType(file))
    }

    /**
     * Attempt to retrieve the thumbnail of given Uri from the MediaStore. This
     * should not be called on the UI thread.
     *
     * @param context
     * @param uri
     * @return
     * @author paulburke
     */
    fun getThumbnail(context: Context, uri: Uri): Bitmap? {
        return getThumbnail(context, uri, getMimeType(context, uri))
    }

    /**
     * Attempt to retrieve the thumbnail of given Uri from the MediaStore. This
     * should not be called on the UI thread.
     *
     * @param context
     * @param uri
     * @param mimeType
     * @return
     * @author paulburke
     */
    fun getThumbnail(context: Context, uri: Uri?, mimeType: String?): Bitmap? {
        if (!isMediaUri(uri)) {
            return null
        }
        var bm: Bitmap? = null
        if (uri != null) {
            val resolver = context.contentResolver
            var cursor: Cursor? = null
            try {
                cursor = resolver.query(uri, null, null, null, null)
                if (cursor!!.moveToFirst()) {
                    val id = cursor.getInt(0)
                    if (mimeType!!.contains("video")) {
                        bm = MediaStore.Video.Thumbnails.getThumbnail(
                            resolver,
                            id.toLong(),
                            MediaStore.Video.Thumbnails.MINI_KIND,
                            null
                        )
                    } else if (mimeType.contains(MIME_TYPE_IMAGE)) {
                        bm = MediaStore.Images.Thumbnails.getThumbnail(
                            resolver,
                            id.toLong(),
                            MediaStore.Images.Thumbnails.MINI_KIND,
                            null
                        )
                    }
                }
            } catch (e: Exception) {
            } finally {
                cursor?.close()
            }
        }
        return bm
    }

    fun resizeBitMapImage(filePath: String?, targetWidth: Int, targetHeight: Int): Bitmap? {
        var bitMapImage: Bitmap? = null
        try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(filePath, options)
            var sampleSize = 0.0
            val scaleByHeight = Math.abs(options.outHeight - targetHeight) >= Math.abs(
                options.outWidth
                        - targetWidth
            )
            if (options.outHeight * options.outWidth * 2 >= 1638) {
                sampleSize =
                    if (scaleByHeight) (options.outHeight / targetHeight).toDouble() else options.outWidth / targetWidth.toDouble()
                sampleSize = 2.0.pow(floor(ln(sampleSize) / ln(2.0))).toInt()
                    .toDouble()
            }
            options.inJustDecodeBounds = false
            options.inTempStorage = ByteArray(128)
            while (true) {
                try {
                    options.inSampleSize = sampleSize.toInt()
                    bitMapImage = BitmapFactory.decodeFile(filePath, options)
                    break
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    try {
                        sampleSize *= 2
                    } catch (ex1: Exception) {
                        ex1.printStackTrace()
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return bitMapImage
    }

    fun saveImage(roolDirectory: File?, bitmap: Bitmap): File? {
        try {
            val file = File(roolDirectory, "thumb_" + System.currentTimeMillis() + "_.jpeg")
            file.parentFile.mkdirs()
            val outputStream = BufferedOutputStream(FileOutputStream(file))
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            return file
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Get the Intent for selecting content to be used in an Intent Chooser.
     *
     * @return The intent for opening a file with Intent.createChooser()
     * @author paulburke
     */
    fun createGetContentIntent(): Intent {
        // Implicitly allow the user to select a particular kind of data
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        // The MIME data type filter
        intent.type = "*/*"
        // Only return URIs that can be opened with ContentResolver
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        return intent
    }

    fun compressImage(filePath: String?): String {
        var scaledBitmap: Bitmap? = null
        val options = BitmapFactory.Options()

        //      by setting this field as true, the actual bitmap pixels are not loaded in the memory. Just the bounds are loaded. If
        //      you try the use the bitmap here, you will get null.
        options.inJustDecodeBounds = true
        var bmp = BitmapFactory.decodeFile(filePath, options)
        var actualHeight = options.outHeight
        var actualWidth = options.outWidth

//      max Height and width values of the compressed image is taken as 816x612
        val maxHeight = 300.0f //1280.0f;//816.0f;
        val maxWidth = 540.0f //852.0f;//612.0f;
        var imgRatio = (actualWidth / actualHeight).toFloat()
        val maxRatio = maxWidth / maxHeight

//      width and height values are set maintaining the aspect ratio of the image
        if (actualHeight > maxHeight || actualWidth > maxWidth) {
            if (imgRatio < maxRatio) {
                imgRatio = maxHeight / actualHeight
                actualWidth = (imgRatio * actualWidth).toInt()
                actualHeight = maxHeight.toInt()
            } else if (imgRatio > maxRatio) {
                imgRatio = maxWidth / actualWidth
                actualHeight = (imgRatio * actualHeight).toInt()
                actualWidth = maxWidth.toInt()
            } else {
                actualHeight = maxHeight.toInt()
                actualWidth = maxWidth.toInt()
            }
        }

//      setting inSampleSize value allows to load a scaled down version of the original image
        options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight)

//      inJustDecodeBounds set to false to load the actual bitmap
        options.inJustDecodeBounds = false

//      this options allow android to claim the bitmap memory if it runs low on memory
        options.inPurgeable = true
        options.inInputShareable = true
        options.inTempStorage = ByteArray(16 * 1024)
        try {
//          load the bitmap from its path
            bmp = BitmapFactory.decodeFile(filePath, options)
        } catch (exception: OutOfMemoryError) {
            exception.printStackTrace()
        }
        try {
            scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888)
        } catch (exception: OutOfMemoryError) {
            exception.printStackTrace()
        }
        val ratioX = actualWidth / options.outWidth.toFloat()
        val ratioY = actualHeight / options.outHeight.toFloat()
        val middleX = actualWidth / 2.0f
        val middleY = actualHeight / 2.0f
        val scaleMatrix = Matrix()
        scaleMatrix.setScale(ratioX, ratioY, middleX, middleY)
        var canvas: Canvas? = null
        if (scaledBitmap != null) {
            canvas = Canvas(scaledBitmap)
            canvas.setMatrix(scaleMatrix)
            canvas.drawBitmap(
                bmp,
                middleX - bmp.width / 2,
                middleY - bmp.height / 2,
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        }

//      check the rotation of the image and display it properly
        val exif: ExifInterface
        try {
            exif = ExifInterface(filePath!!)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, 0
            )
            val matrix = Matrix()
            if (orientation == 6) {
                matrix.postRotate(90f)
            } else if (orientation == 3) {
                matrix.postRotate(180f)
            } else if (orientation == 8) {
                matrix.postRotate(270f)
            }
            if (scaledBitmap != null) {
                scaledBitmap = Bitmap.createBitmap(
                    scaledBitmap, 0, 0,
                    scaledBitmap.width, scaledBitmap.height, matrix,
                    true
                )
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        var out: FileOutputStream? = null
        val filename = filename
        try {
            out = FileOutputStream(filename)

//          write the compressed bitmap at the destination specified by filename.
            scaledBitmap?.compress(Bitmap.CompressFormat.JPEG, 80, out)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        return filename
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }
        val totalPixels = (width * height).toFloat()
        val totalReqPixelsCap = (reqWidth * reqHeight * 2).toFloat()
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++
        }
        return inSampleSize
    }

    private val filename: String
        private get() {
            val file = File(
                Environment.getExternalStorageDirectory().path,
                "IRP/Images"
            )
            if (!file.exists()) {
                file.mkdirs()
            }
            return file.absolutePath + "/" + System.currentTimeMillis() + ".jpg"
        }
}