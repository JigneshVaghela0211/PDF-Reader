package com.pdf.pdfreader.ui

import android.animation.Animator
import android.view.View
import com.pdf.pdfreader.base.BaseActivity
import com.pdf.pdfreader.databinding.LauncherActivityBinding
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
        binding.animation.addAnimatorUpdateListener {
            it.addListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator) {
                    try {
                        loadActivity(HomeActivity::class.java).byFinishingCurrent().start()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {

                }

                override fun onAnimationRepeat(animation: Animator) {
                }

                override fun onAnimationStart(animation: Animator) {
                }

                override fun onAnimationEnd(animation: Animator, isReverse: Boolean) {
                    super.onAnimationEnd(animation, isReverse)
                }
            })
        }
    }


}