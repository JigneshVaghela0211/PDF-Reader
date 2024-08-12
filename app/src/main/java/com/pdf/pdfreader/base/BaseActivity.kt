package com.pdf.pdfreader.base

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.pdf.pdfreader.R
import javax.inject.Inject

abstract class BaseActivity : AppCompatActivity(), HasToolbar, Navigator {

    @Inject
    lateinit var navigationFactory: FragmentNavigationFactory

    @Inject
    lateinit var activityStarter: ActivityStarter

    private var alertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContentView(createViewBinding())
        setUpAlertDialog()

      }


    private fun setUpAlertDialog() {
        alertDialog =
            AlertDialog.Builder(this).setPositiveButton("ok", null).setTitle(R.string.app_name)
                .create()
    }

    fun <F : BaseFragment<*>> getCurrentFragment(): F? {
        return if (findFragmentPlaceHolder() == 0) null else supportFragmentManager.findFragmentById(
            findFragmentPlaceHolder()
        ) as F?
    }

    abstract fun findFragmentPlaceHolder(): Int

    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    protected fun shouldGoBack(): Boolean {
        return true
    }

    override fun onBackPressed() {
        hideKeyboard()


        val currentFragment = getCurrentFragment<BaseFragment<*>>()
        if (currentFragment == null) super.onBackPressed()
        else if (currentFragment.onBackActionPerform() && shouldGoBack()) super.onBackPressed()
    }

    fun hideKeyboard() {
        // Check if no view has focus:

        val view = this.currentFocus
        if (view != null) {
            val inputManager =
                this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputManager.hideSoftInputFromWindow(
                view.windowToken, InputMethodManager.HIDE_NOT_ALWAYS
            )
        }

    }

    private var progress: Dialog? = null
    fun showLoadingDialog(toShow: Boolean) {
        if (toShow) {
            if (progress == null) {
                progress = Dialog(this)
            }
            if (progress?.isShowing == false) {
                progress?.show()
            }
        } else {
            if (progress?.isShowing == true) {
                progress?.dismiss()
            }
        }
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun setToolbar(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
    }

    override fun showToolbar(b: Boolean) {
        val supportActionBar = supportActionBar
        if (supportActionBar != null) {

            if (b) supportActionBar.show()
            else supportActionBar.hide()
        }
    }

    override fun setToolbarTitle(title: CharSequence) {
        if (supportActionBar != null) {
            supportActionBar!!.title = title
        }
    }

    override fun setToolbarTitle(@StringRes title: Int) {

        if (supportActionBar != null) {
            supportActionBar!!.setTitle(title)
        }
    }

    override fun showBackButton(b: Boolean) {

        val supportActionBar = supportActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(b)
    }

    override fun setToolbarColor(@ColorRes color: Int) {}

    fun showSnackBar(message: String) {
        hideKeyboard()
        val viewNew: View? = findViewById(android.R.id.content)
        if (viewNew != null) {
            val snackBar = Snackbar.make(viewNew, message, Snackbar.LENGTH_LONG)
            snackBar.duration = 2000
            snackBar.setActionTextColor(Color.WHITE)
            val snackView = snackBar.view
            val params = snackView.layoutParams as FrameLayout.LayoutParams
            params.gravity = Gravity.TOP
            snackView.layoutParams = params
            val textView: TextView =
                snackView.findViewById(com.google.android.material.R.id.snackbar_text)
            textView.setTextColor(ContextCompat.getColor(this, R.color.white))
            textView.maxLines = 4

            snackView.background =
                ResourcesCompat.getDrawable(resources, R.color.black, null)
            snackBar.animationMode = BaseTransientBottomBar.ANIMATION_MODE_FADE
            snackBar.show()
        }
    }


    fun logout() {
    }

    override fun setToolbarElevation(isVisible: Boolean) {

        if (supportActionBar != null) {
            supportActionBar!!.elevation = if (isVisible) 8f else 0f
        }
    }



    fun showKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val inputManager =
                this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }


    override fun <T : BaseFragment<*>> load(tClass: Class<T>): FragmentActionPerformer<T> {
        return navigationFactory.make(tClass)
    }

    override fun loadActivity(aClass: Class<out BaseActivity>): ActivityBuilder {
        return activityStarter.make(aClass)
    }

    override fun <T : BaseFragment<*>> loadActivity(
        aClass: Class<out BaseActivity>, pageTClass: Class<T>
    ): ActivityBuilder {
        return activityStarter.make(aClass).setPage(pageTClass)
    }


    override fun goBack() {
        onBackPressed()
    }
}