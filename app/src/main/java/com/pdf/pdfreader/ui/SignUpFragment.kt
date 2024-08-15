package com.pdf.pdfreader.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import com.pdf.pdfreader.base.BaseFragment
import com.pdf.pdfreader.databinding.SignupFragmentBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignUpFragment : BaseFragment<SignupFragmentBinding>() {
    override fun createViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        attachToRoot: Boolean
    ): SignupFragmentBinding {
        return SignupFragmentBinding.inflate(layoutInflater)
    }

    override fun bindData() {
        binding.buttonNext.setOnClickListener {
            navigator.loadActivity(HomeActivity::class.java).start()
        }
        binding.buttonBack.setOnClickListener {
            navigator.goBack()
        }
    }
}