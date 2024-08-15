package com.pdf.pdfreader.ui.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import com.pdf.pdfreader.base.BaseFragment
import com.pdf.pdfreader.databinding.RecentFragmentBinding
import com.pdf.pdfreader.ui.activity.HomeActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignUpFragment : BaseFragment<RecentFragmentBinding>() {
    override fun createViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        attachToRoot: Boolean
    ): RecentFragmentBinding {
        return RecentFragmentBinding.inflate(layoutInflater)
    }

    override fun bindData() {
        binding.buttonNext.setOnClickListener {
//            navigator.loadActivity(HomeActivity::class.java).start()
        }
        binding.buttonBack.setOnClickListener {
            navigator.goBack()
        }
    }
}