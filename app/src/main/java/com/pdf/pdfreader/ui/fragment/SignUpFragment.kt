package com.pdf.pdfreader.ui.fragment

import com.pdf.pdfreader.base.BaseFragment
import com.pdf.pdfreader.databinding.RecentFragmentBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignUpFragment : BaseFragment<RecentFragmentBinding>() {
    override fun createViewBinding() = RecentFragmentBinding.inflate(layoutInflater)


    override fun bindData() {
        binding.buttonNext.setOnClickListener {
//            navigator.loadActivity(HomeActivity::class.java).start()
        }
        binding.buttonBack.setOnClickListener {
            navigator.goBack()
        }
    }
}