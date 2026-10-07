package com.facefit.kiosk.ui.scan

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.facefit.kiosk.R
import com.facefit.kiosk.data.session.KioskSessionStore
import com.facefit.kiosk.databinding.ScreenFacePositioningBinding
import com.facefit.kiosk.databinding.ViewScanGuidanceRowBinding
import com.facefit.kiosk.facefit.camera.CameraCaptureGateway
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator
import com.facefit.kiosk.permissions.CameraPermissionController
import java.io.File
import java.io.IOException

class KioskFacePositioningView(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val cameraGateway: CameraCaptureGateway,
    private val permissionController: CameraPermissionController,
    private val sessionStore: KioskSessionStore,
    private val navigator: KioskNavigator
) : FrameLayout(context) {

    private val binding = ScreenFacePositioningBinding.inflate(LayoutInflater.from(context), this, true)
    private var currentState: FacePositioningUiState = FacePositioningUiState.CameraStarting
    private var isViewAttached = false

    init {
        bindBaseGuidance()
        applyState(FacePositioningUiState.CameraStarting)
        binding.captureButton.setOnClickListener { handlePrimaryAction() }
        binding.retakeButton.setOnClickListener { retakeCapture() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isViewAttached = true
        startCameraIfAllowed()
    }

    override fun onDetachedFromWindow() {
        isViewAttached = false
        cameraGateway.stopPreview()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus &&
            permissionController.isCameraPermissionGranted() &&
            currentState.canRestartCamera()
        ) {
            startLivePreview()
        }
    }

    private fun bindBaseGuidance() {
        bindRow(
            binding.faceCenteredRow,
            title = context.getString(R.string.scan_guidance_face_ready_title),
            body = context.getString(R.string.scan_guidance_face_ready_body)
        )
        bindRow(
            binding.lightingRow,
            title = context.getString(R.string.scan_guidance_lighting_title),
            body = context.getString(R.string.scan_guidance_lighting_body)
        )
        bindRow(
            binding.positionRow,
            title = context.getString(R.string.scan_guidance_position_title),
            body = context.getString(R.string.scan_guidance_position_body)
        )
    }

    private fun bindRow(row: ViewScanGuidanceRowBinding, title: String, body: String) {
        row.guidanceTitleText.text = title
        row.guidanceBodyText.text = body
    }

    private fun handlePrimaryAction() {
        when (val state = currentState) {
            FacePositioningUiState.CameraStarting,
            FacePositioningUiState.CaptureInProgress -> Unit
            FacePositioningUiState.PermissionRequired -> requestCameraPermission()
            FacePositioningUiState.PermissionPermanentlyDenied -> permissionController.openAppSettings()
            FacePositioningUiState.CameraUnavailable -> startCameraIfAllowed()
            FacePositioningUiState.Ready,
            FacePositioningUiState.CaptureFailed -> captureStillImage()
            is FacePositioningUiState.Captured -> continueWithCapture(state.imageFile)
        }
    }

    private fun startCameraIfAllowed() {
        if (!permissionController.isCameraPermissionGranted()) {
            applyState(FacePositioningUiState.PermissionRequired)
            return
        }
        startLivePreview()
    }

    private fun startLivePreview() {
        applyState(FacePositioningUiState.CameraStarting)
        cameraGateway.startPreview(
            lifecycleOwner = lifecycleOwner,
            previewView = binding.cameraPreviewView,
            onReady = {
                if (isViewAttached) {
                    applyState(FacePositioningUiState.Ready)
                } else {
                    cameraGateway.stopPreview()
                }
            },
            onUnavailable = {
                if (isViewAttached) {
                    applyState(FacePositioningUiState.CameraUnavailable)
                }
            }
        )
    }

    private fun requestCameraPermission() {
        binding.captureButton.isEnabled = false
        permissionController.requestCameraPermission { isGranted ->
            if (isGranted) {
                startLivePreview()
            } else if (permissionController.shouldShowCameraPermissionRationale()) {
                applyState(FacePositioningUiState.PermissionRequired)
            } else {
                applyState(FacePositioningUiState.PermissionPermanentlyDenied)
            }
        }
    }

    private fun captureStillImage() {
        val outputFile = try {
            discardStoredCapture()
            createCaptureFile()
        } catch (error: IOException) {
            applyState(FacePositioningUiState.CaptureFailed)
            return
        }

        applyState(FacePositioningUiState.CaptureInProgress)
        cameraGateway.captureImage(
            outputFile = outputFile,
            onSuccess = { imageFile ->
                if (!isViewAttached) return@captureImage
                sessionStore.capturedFaceImagePath = imageFile.absolutePath
                cameraGateway.stopPreview()
                applyState(FacePositioningUiState.Captured(imageFile))
            },
            onError = {
                outputFile.delete()
                if (isViewAttached) {
                    applyState(FacePositioningUiState.CaptureFailed)
                }
            }
        )
    }

    private fun retakeCapture() {
        discardStoredCapture()
        binding.capturedImageView.setImageDrawable(null)
        startCameraIfAllowed()
    }

    private fun continueWithCapture(imageFile: File) {
        sessionStore.capturedFaceImagePath = imageFile.absolutePath
        navigator.navigateTo(KioskDestination.Processing)
    }

    private fun applyState(state: FacePositioningUiState) {
        currentState = state
        when (state) {
            FacePositioningUiState.CameraStarting -> showCameraStartingState()
            FacePositioningUiState.PermissionRequired -> showPermissionRequiredState()
            FacePositioningUiState.PermissionPermanentlyDenied -> showPermissionPermanentlyDeniedState()
            FacePositioningUiState.CameraUnavailable -> showCameraUnavailableState()
            FacePositioningUiState.Ready -> showReadyState()
            FacePositioningUiState.CaptureInProgress -> showCaptureInProgressState()
            FacePositioningUiState.CaptureFailed -> showCaptureFailedState()
            is FacePositioningUiState.Captured -> showCapturedState(state.imageFile)
        }
    }

    private fun showCameraStartingState() {
        showLiveCameraState(
            status = context.getString(R.string.scan_status_camera_starting),
            body = context.getString(R.string.scan_body_camera_starting),
            tip = context.getString(R.string.scan_tip_camera_starting),
            previewMessage = context.getString(R.string.scan_preview_camera_starting),
            buttonText = context.getString(R.string.scan_capture_button),
            isButtonEnabled = false,
            statusBackground = R.drawable.bg_status_warning_pill
        )
    }

    private fun showReadyState() {
        showLiveCameraState(
            status = context.getString(R.string.scan_status_ready),
            body = context.getString(R.string.scan_body),
            tip = context.getString(R.string.scan_tip_ready),
            previewMessage = context.getString(R.string.scan_preview_ready),
            buttonText = context.getString(R.string.scan_capture_button),
            isButtonEnabled = true,
            statusBackground = R.drawable.bg_status_ready_pill
        )
    }

    private fun showCaptureInProgressState() {
        showLiveCameraState(
            status = context.getString(R.string.scan_status_capturing),
            body = context.getString(R.string.scan_body_capturing),
            tip = context.getString(R.string.scan_tip_capturing),
            previewMessage = context.getString(R.string.scan_preview_capturing),
            buttonText = context.getString(R.string.scan_capture_button),
            isButtonEnabled = false,
            statusBackground = R.drawable.bg_status_warning_pill
        )
    }

    private fun showCaptureFailedState() {
        showLiveCameraState(
            status = context.getString(R.string.scan_status_capture_failed),
            body = context.getString(R.string.scan_body_capture_failed),
            tip = context.getString(R.string.scan_tip_capture_failed),
            previewMessage = context.getString(R.string.scan_preview_capture_failed),
            buttonText = context.getString(R.string.scan_capture_button),
            isButtonEnabled = true,
            statusBackground = R.drawable.bg_status_error_pill,
            statusTextColor = R.color.facefit_error
        )
    }

    private fun showLiveCameraState(
        status: String,
        body: String,
        tip: String,
        previewMessage: String,
        buttonText: String,
        isButtonEnabled: Boolean,
        statusBackground: Int,
        statusTextColor: Int = R.color.facefit_ink
    ) {
        binding.cameraPreviewView.visibility = View.VISIBLE
        binding.capturedImageView.visibility = View.GONE
        binding.faceGuide.visibility = View.VISIBLE
        binding.cameraIssueText.visibility = View.GONE
        binding.retakeButton.visibility = View.GONE

        binding.cameraStatusText.setBackgroundResource(statusBackground)
        binding.cameraStatusText.setTextColor(context.getColor(statusTextColor))
        binding.cameraStatusText.text = status
        binding.previewMessageText.text = previewMessage
        binding.instructionBodyText.text = body
        binding.tipText.text = tip
        binding.captureButton.text = buttonText
        binding.captureButton.isEnabled = isButtonEnabled
        bindBaseGuidance()
    }

    private fun showPermissionRequiredState() {
        showCameraBlockedState(
            status = context.getString(R.string.scan_status_permission_denied),
            issue = context.getString(R.string.scan_issue_permission_denied),
            tip = context.getString(R.string.scan_tip_permission_denied),
            buttonText = context.getString(R.string.scan_request_permission_button)
        )
    }

    private fun showPermissionPermanentlyDeniedState() {
        showCameraBlockedState(
            status = context.getString(R.string.scan_status_permission_denied),
            issue = context.getString(R.string.scan_issue_permission_permanently_denied),
            tip = context.getString(R.string.scan_tip_permission_permanently_denied),
            buttonText = context.getString(R.string.scan_open_settings_button)
        )
    }

    private fun showCameraUnavailableState() {
        showCameraBlockedState(
            status = context.getString(R.string.scan_status_camera_unavailable),
            issue = context.getString(R.string.scan_issue_camera_unavailable),
            tip = context.getString(R.string.scan_tip_camera_unavailable),
            buttonText = context.getString(R.string.scan_retry_camera_button)
        )
    }

    private fun showCameraBlockedState(status: String, issue: String, tip: String, buttonText: String) {
        binding.cameraPreviewView.visibility = View.GONE
        binding.capturedImageView.visibility = View.GONE
        binding.faceGuide.visibility = View.GONE
        binding.cameraIssueText.visibility = View.VISIBLE
        binding.cameraIssueText.text = issue
        binding.retakeButton.visibility = View.GONE

        binding.cameraStatusText.setBackgroundResource(R.drawable.bg_status_error_pill)
        binding.cameraStatusText.setTextColor(context.getColor(R.color.facefit_error))
        binding.cameraStatusText.text = status
        binding.previewMessageText.text = context.getString(R.string.scan_preview_camera_issue)
        binding.instructionBodyText.text = context.getString(R.string.scan_body_camera_issue)
        binding.tipText.text = tip
        binding.captureButton.text = buttonText
        binding.captureButton.isEnabled = true
    }

    private fun showCapturedState(imageFile: File) {
        binding.cameraPreviewView.visibility = View.GONE
        binding.capturedImageView.visibility = View.VISIBLE
        binding.capturedImageView.setImageURI(Uri.fromFile(imageFile))
        binding.faceGuide.visibility = View.GONE
        binding.cameraIssueText.visibility = View.GONE
        binding.retakeButton.visibility = View.VISIBLE

        binding.cameraStatusText.setBackgroundResource(R.drawable.bg_status_ready_pill)
        binding.cameraStatusText.setTextColor(context.getColor(R.color.facefit_ink))
        binding.cameraStatusText.text = context.getString(R.string.scan_status_captured)
        binding.previewMessageText.text = context.getString(R.string.scan_preview_captured)
        binding.instructionBodyText.text = context.getString(R.string.scan_body_captured)
        binding.tipText.text = context.getString(R.string.scan_tip_captured)
        binding.captureButton.text = context.getString(R.string.scan_capture_continue_button)
        binding.captureButton.isEnabled = true
        bindBaseGuidance()
    }

    private fun discardStoredCapture() {
        sessionStore.faceLandmarkScan = null
        sessionStore.latestFaceAnalysisResult = null
        sessionStore.capturedFaceImagePath?.let { File(it).delete() }
        sessionStore.capturedFaceImagePath = null
    }

    private fun createCaptureFile(): File {
        val captureDirectory = File(context.cacheDir, CAPTURE_DIRECTORY_NAME)
        if (!captureDirectory.exists() && !captureDirectory.mkdirs()) {
            throw IOException("Could not create temporary capture directory.")
        }

        val outputFile = File(captureDirectory, CAPTURE_FILE_NAME)
        if (outputFile.exists() && !outputFile.delete()) {
            throw IOException("Could not replace previous temporary capture.")
        }
        return outputFile
    }

    private fun FacePositioningUiState.canRestartCamera(): Boolean {
        return this is FacePositioningUiState.PermissionRequired ||
            this is FacePositioningUiState.PermissionPermanentlyDenied ||
            this is FacePositioningUiState.CameraUnavailable
    }

    private companion object {
        const val CAPTURE_DIRECTORY_NAME = "facefit-captures"
        const val CAPTURE_FILE_NAME = "latest-face-capture.jpg"
    }
}

private sealed class FacePositioningUiState {
    data object CameraStarting : FacePositioningUiState()
    data object PermissionRequired : FacePositioningUiState()
    data object PermissionPermanentlyDenied : FacePositioningUiState()
    data object CameraUnavailable : FacePositioningUiState()
    data object Ready : FacePositioningUiState()
    data object CaptureInProgress : FacePositioningUiState()
    data object CaptureFailed : FacePositioningUiState()
    data class Captured(val imageFile: File) : FacePositioningUiState()
}
