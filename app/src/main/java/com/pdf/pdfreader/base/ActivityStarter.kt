package com.pdf.pdfreader.base


import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair

import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class ActivityStarter @Inject internal constructor(val baseActivity: BaseActivity) {
    private var intent: Intent? = null
    private var activity: Class<out Activity>? = null
    private var shouldAnimate = true

    fun make(activityClass: Class<out BaseActivity>): ActivityBuilder {
        activity = activityClass
        intent = Intent(baseActivity, activityClass)
        return Builder()
    }

    private inner class Builder : ActivityBuilder {
        private var bundle: Bundle? = null
        private var activityOptionsBundle: Bundle? = null
        private var isToFinishCurrent: Boolean = false
        private var requestCode: Int = 0
        private var startForResult: ActivityResultLauncher<Intent>? = null

        override fun start() {
            if (bundle != null) intent!!.putExtras(bundle!!)

            if (!shouldAnimate) intent!!.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

            if (startForResult != null) {
                //startForResult flow with launcher
                startForResult?.launch(intent!!)
            } else if (requestCode != 0) {
                //startForResult flow with request code
                val currentFragment = baseActivity.getCurrentFragment<BaseFragment<*>>()
                if (currentFragment != null)
                    currentFragment.startActivityForResult(intent, requestCode)
                else
                    baseActivity.startActivityForResult(intent!!, requestCode)
            } else {
                //normal case
                if (activityOptionsBundle == null)
                    baseActivity.startActivity(intent)
                else
                    baseActivity.startActivity(intent, activityOptionsBundle)
            }

            // for only requestCode flow
            /*if (requestCode == 0) {
                if (activityOptionsBundle == null)
                    baseActivity.startActivity(intent)
                else
                    baseActivity.startActivity(intent, activityOptionsBundle)
            } else {
                val currentFragment = baseActivity.getCurrentFragment<BaseFragment<*>>()
                if (currentFragment != null) currentFragment.startActivityForResult(
                    intent, requestCode
                )
                else baseActivity.startActivityForResult(intent, requestCode)
            }*/

            // for only launcher flow
            /*if (startForResult == null) {
                if (activityOptionsBundle == null)
                    baseActivity.startActivity(intent)
                else
                    baseActivity.startActivity(intent, activityOptionsBundle)
            } else {
                //val currentFragment = context.getCurrentFragment<BaseFragment<*>>()
                startForResult!!.launch(intent)
            }*/

            /*if (shouldAnimate)
                context.overridePendingTransition(R.anim.slide_enter, R.anim.slide_exit);*/

            if (isToFinishCurrent) baseActivity.finish()
        }

        override fun addBundle(bundle: Bundle): ActivityBuilder {
            if (this.bundle != null) this.bundle!!.putAll(bundle)
            else this.bundle = bundle
            return this
        }

        override fun addSharedElements(pairs: List<Pair<View, String>>): ActivityBuilder {
            val optionsCompat = ActivityOptionsCompat.makeSceneTransitionAnimation(
                baseActivity, *pairs.toTypedArray()
            )

            activityOptionsBundle = optionsCompat.toBundle()
            return this
        }

        override fun byFinishingCurrent(): ActivityBuilder {
            isToFinishCurrent = true
            return this
        }

        override fun byFinishingAll(): ActivityBuilder {
            intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            return this
        }

        override fun <T : BaseFragment<*>> setPage(page: Class<T>): ActivityBuilder {
            intent!!.putExtra(ACTIVITY_FIRST_PAGE, page)
            return this
        }
        
        @Deprecated("This method has been deprecated", level = DeprecationLevel.ERROR)
        override fun forResult(requestCode: Int): ActivityBuilder {
            this.requestCode = requestCode
            return this
        }

        override fun forResult(startForResult: ActivityResultLauncher<Intent>): ActivityBuilder {
            this.startForResult = startForResult
            return this
        }

        override fun shouldAnimate(isAnimate: Boolean): ActivityBuilder {
            shouldAnimate = isAnimate
            return this
        }
    }

    companion object {
        const val ACTIVITY_FIRST_PAGE = "first_page"
    }
}
