package com.pdf.pdfreader.extension

import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.fragment.app.Fragment

fun Fragment.hasPermissionGranted(permission: String) =
    checkSelfPermission(requireActivity(), permission) ==
            PermissionChecker.PERMISSION_GRANTED

@RequiresApi(Build.VERSION_CODES.R)
fun hasAllFilesPermission() = Environment.isExternalStorageManager()

inline fun after(milli: Int, crossinline action: () -> Unit) {
    Handler(Looper.getMainLooper()).postDelayed({
        action()
    }, milli.toLong())
}