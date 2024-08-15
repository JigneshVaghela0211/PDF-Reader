package com.pdf.pdfreader.extension

import android.app.Activity
import android.util.Log
import androidx.fragment.app.Fragment


fun eLog(tag: String, message: String) {
    printLog(tag, message)
}

fun Fragment.eLog(tag: String, message: String) {
    printLog(tag, message)
}

fun Fragment.eLog(message: String) {
    printLog(this::class.java.simpleName, message)
}

fun Activity.eLog(tag: String, message: String) {
    printLog(tag, message)
}

fun Activity.eLog(message: String) {
    printLog(this::class.java.simpleName, message)
}

fun Any.eLog(tag: String, message: String) {
    printLog(tag, message)
}

fun Any.eLog(message: String) {
    printLog(this::class.java.simpleName, message)
}

fun printLog(tag: String, message: String) {
//    if (BuildConfig.DEBUG) {
    Log.e("OkH $tag", message)
//    }
}