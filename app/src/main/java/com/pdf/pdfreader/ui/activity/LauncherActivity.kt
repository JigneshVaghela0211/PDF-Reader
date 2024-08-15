package com.pdf.pdfreader.ui.activity

import android.view.View
import com.pdf.pdfreader.base.BaseActivity
import com.pdf.pdfreader.databinding.LauncherActivityBinding
import com.pdf.pdfreader.extension.after
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LauncherActivity : BaseActivity() {
    private lateinit var binding: LauncherActivityBinding

    override fun findFragmentPlaceHolder(): Int {
        return 0
    }

    override fun createViewBinding(): View {
        binding = LauncherActivityBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun bindData() {
        after(3000) {
            loadActivity(HomeActivity::class.java).byFinishingCurrent().start()
        }
    }


}