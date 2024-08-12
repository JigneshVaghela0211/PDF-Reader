package com.pdf.pdfreader

import android.os.Bundle
import com.pdf.pdfreader.base.BaseActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun findFragmentPlaceHolder(): Int {
        return 0
    }


}