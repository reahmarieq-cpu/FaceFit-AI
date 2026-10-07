package com.facefit.kiosk.ui.consent

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.facefit.kiosk.R
import com.facefit.kiosk.databinding.ScreenCameraConsentBinding
import com.facefit.kiosk.databinding.ViewConsentRowBinding
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class KioskConsentView(
    context: Context,
    private val navigator: KioskNavigator
) : FrameLayout(context) {

    private val binding = ScreenCameraConsentBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        bindConsentRows()
        binding.agreeButton.setOnClickListener {
            navigator.navigateTo(KioskDestination.FacePositioning)
        }
        binding.cancelButton.setOnClickListener {
            showCancelDialog()
        }
    }

    private fun bindConsentRows() {
        bindRow(
            binding.cameraAccessRow,
            title = context.getString(R.string.consent_camera_access_title),
            body = context.getString(R.string.consent_camera_access_body)
        )
        bindRow(
            binding.facialProcessingRow,
            title = context.getString(R.string.consent_facial_processing_title),
            body = context.getString(R.string.consent_facial_processing_body)
        )
        bindRow(
            binding.sessionDataRow,
            title = context.getString(R.string.consent_session_data_title),
            body = context.getString(R.string.consent_session_data_body)
        )
    }

    private fun bindRow(rowBinding: ViewConsentRowBinding, title: String, body: String) {
        rowBinding.consentTitleText.text = title
        rowBinding.consentBodyText.text = body
    }

    private fun showCancelDialog() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.consent_cancel_dialog_title)
            .setMessage(R.string.consent_cancel_dialog_body)
            .setNegativeButton(R.string.consent_cancel_dialog_stay, null)
            .setPositiveButton(R.string.consent_cancel_dialog_return) { _, _ ->
                navigator.navigateTo(KioskDestination.Welcome)
            }
            .show()
    }
}

