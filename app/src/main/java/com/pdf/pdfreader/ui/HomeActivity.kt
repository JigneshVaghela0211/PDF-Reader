package com.pdf.pdfreader.ui

import android.view.View
import com.pdf.pdfreader.base.BaseActivity
import com.pdf.pdfreader.databinding.HomeActivityBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity : BaseActivity() {
    private lateinit var binding: HomeActivityBinding

    override fun createViewBinding(): View {
        binding = HomeActivityBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun findFragmentPlaceHolder(): Int {
        return 0
    }

    override fun bindData() {

    }


}