package com.pdf.pdfreader.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.pdf.pdfreader.BuildConfig
import com.pdf.pdfreader.base.BaseFragment
import com.pdf.pdfreader.databinding.AllFileFragmentBinding
import com.pdf.pdfreader.extension.hasAllFilesPermission
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AllFileFragment : BaseFragment<AllFileFragmentBinding>() {
    override fun createViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        attachToRoot: Boolean
    ): AllFileFragmentBinding {
        return AllFileFragmentBinding.inflate(layoutInflater)
    }

    override fun onResume() {
        super.onResume()
        permissionStatus()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun bindData() {
        binding.permissionRequest.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 30) {
                if (hasAllFilesPermission()) {
                    Toast.makeText(this.requireContext(), "Granted", Toast.LENGTH_LONG)
                        .show()
                    return@setOnClickListener
                }

                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                    )
                )
            } else {
                Toast.makeText(requireContext(), "Sorry", Toast.LENGTH_LONG).show()
            }

        }
        permissionStatus()
    }

    private fun permissionStatus() {
        if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hasAllFilesPermission()
            } else {
                return
            }
        ) {
            binding.textViewStatus.text = "Grant"
        } else {
            binding.textViewStatus.text = "Deny"
        }
    }
}