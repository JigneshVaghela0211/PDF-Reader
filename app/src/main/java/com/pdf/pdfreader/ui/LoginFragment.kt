package com.pdf.pdfreader.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import com.pdf.pdfreader.base.BaseFragment
import com.pdf.pdfreader.databinding.LoginFragmentBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : BaseFragment<LoginFragmentBinding>() {
    override fun createViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        attachToRoot: Boolean
    ): LoginFragmentBinding {
        return LoginFragmentBinding.inflate(layoutInflater)
    }

    override fun bindData() {
        binding.buttonNext.setOnClickListener {
            navigator.load(SignUpFragment::class.java).replace(true)
        }
        binding.buttonBack.setOnClickListener {
            navigator.goBack()
        }
    }
}