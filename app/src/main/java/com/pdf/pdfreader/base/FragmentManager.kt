package com.pdf.pdfreader.base

import android.util.Pair
import android.view.View

import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import javax.inject.Named

@ActivityScoped
class FragmentManager @Inject constructor(
    private val baseActivity: BaseActivity,
    @param:Named(DiConstants.PLACEHOLDER) private val placeHolder: Int
) : FragmentHandler {

    override fun openFragment(
        baseFragment: BaseFragment<*>,
        option: FragmentHandler.Option,
        isToBackStack: Boolean,
        tag: String,
        sharedElements: List<Pair<View, String>>?
    ) {
        val fragmentTransaction = baseActivity.supportFragmentManager.beginTransaction()
        when (option) {
            FragmentHandler.Option.ADD -> fragmentTransaction.add(placeHolder, baseFragment, tag)
            FragmentHandler.Option.REPLACE -> fragmentTransaction.replace(
                placeHolder, baseFragment, tag
            )
            FragmentHandler.Option.SHOW -> if (baseFragment.isAdded) fragmentTransaction.show(
                baseFragment
            )
            FragmentHandler.Option.HIDE -> if (baseFragment.isAdded) fragmentTransaction.hide(
                baseFragment
            )
        }

        if (isToBackStack) fragmentTransaction.addToBackStack(tag)
        fragmentTransaction.commitAllowingStateLoss()
    }

    override fun showFragment(
        fragmentToShow: BaseFragment<*>, vararg fragmentToHide: BaseFragment<*>
    ) {
        val fragmentTransaction =
            baseActivity.supportFragmentManager.beginTransaction()
        if (fragmentToShow.isAdded) {
            fragmentTransaction.show(fragmentToShow)
            fragmentToShow.onShow()
        } else openFragment(fragmentToShow, FragmentHandler.Option.ADD, false, "home", null)

        for (fragment in fragmentToHide) {
            if (fragment.isAdded) fragmentTransaction.hide(fragment)
        }
        fragmentTransaction.commit()
    }

    override fun clearFragmentHistory(tag: String?) {
        sDisableFragmentAnimations = true
        baseActivity.supportFragmentManager.popBackStackImmediate(
            tag, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        sDisableFragmentAnimations = false
    }

    companion object {
        var sDisableFragmentAnimations = false
    }
}
