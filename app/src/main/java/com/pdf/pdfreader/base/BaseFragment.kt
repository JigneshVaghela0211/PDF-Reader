package com.pdf.pdfreader.base

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.inject.Inject

abstract class BaseFragment<T : ViewBinding> : Fragment() {

    protected lateinit var toolbar: HasToolbar


    private var _binding: T? = null

    protected val binding: T
        get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = createViewBinding(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindData()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (activity is HasToolbar) {
            toolbar = activity as HasToolbar
        }
    }


    fun hideKeyBoard() {
        if (activity is BaseActivity) {
            (activity as BaseActivity).hideKeyboard()
        }
    }

    fun showKeyBoard() {
        if (activity is BaseActivity) {
            (activity as BaseActivity).showKeyboard()
        }
    }

    fun <T : BaseFragment<*>> getParentFragment(targetFragment: Class<T>): T? {
        if (parentFragment == null) return null
        try {
            return targetFragment.cast(parentFragment)
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }
        return null
    }


    open fun onShow() {

    }


    fun showMessage(message: String) {
        showSnackBar(message)

    }

    fun showToast(message: String) {
//        if (BuildConfig.DEBUG) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
//        }
    }

    fun showMessage(@StringRes stringId: Int) {
        showSnackBar(getString(stringId))
    }


    private fun showSnackBar(message: String) {
        if (activity is BaseActivity) {
            (activity as BaseActivity).showSnackBar(message)
        }
    }




    open fun onBackActionPerform(): Boolean {
        return true
    }

    open fun onViewClick(view: View) {

    }



    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    fun logout() {
        if (isAdded) {
            NotificationManagerCompat.from(requireContext()).cancelAll()
        }
    }

    /**
     * This method is used for binding view with your binding
     */
    protected abstract fun createViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        attachToRoot: Boolean
    ): T

    protected abstract fun bindData()
}
