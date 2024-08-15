package com.pdf.pdfreader.ui.activity

import android.view.View
import com.pdf.pdfreader.R
import com.pdf.pdfreader.base.BaseActivity
import com.pdf.pdfreader.databinding.HomeActivityBinding
import com.pdf.pdfreader.ui.fragment.AllFileFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity : BaseActivity() {
    private lateinit var binding: HomeActivityBinding

    override fun createViewBinding(): View {
        binding = HomeActivityBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun findFragmentPlaceHolder(): Int {
        return R.id.container
    }

    override fun bindData() {
        load(AllFileFragment::class.java).replace(false)
    }


}