package com.pdf.pdfreader.base

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar

interface HasToolbar {

    fun setToolbar(toolbar: Toolbar)

    fun showToolbar(b: Boolean)

    fun setToolbarTitle(title: CharSequence)

    fun setToolbarTitle(@StringRes title: Int)

    fun showBackButton(b: Boolean)

    fun setToolbarColor(@ColorRes color: Int)

    fun setToolbarElevation(isVisible: Boolean)
}
