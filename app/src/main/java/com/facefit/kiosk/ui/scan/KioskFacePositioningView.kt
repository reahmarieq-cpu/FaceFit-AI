package com.facefit.kiosk.ui.scan

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.facefit.kiosk.R
import com.facefit.kiosk.databinding.ScreenFacePositioningBinding
import com.facefit.kiosk.databinding.ViewScanGuidanceRowBinding
import com.facefit.kiosk.facefit.camera.CameraCaptureGateway
import com.facefit.kiosk.facefit.camera.CameraReadiness
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator

class KioskFacePositioningView(
    context: Context,
    private val cameraGateway: CameraCaptureGateway,
    private val navigator: KioskNavigator
) : FrameLayout(context) {

    private val binding = ScreenFacePositioningBinding.inflate(LayoutInflater.from(context), this, true)
    private var currentState: FacePositioningUiState = FacePositioningUiState.Ready
    private var mockStateIndex = 0

    init {
        bindBaseGuidance()
        applyState(stateFromCameraReadiness(cameraGateway.requestCameraReadiness()))

        binding.captureButton.setOnClickListener {
            handleCapture()
        }
        binding.cameraPreviewPanel.setOnLongClickListener {
            cycleMockStateForReview()
            true
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

    private fun handleCapture() {
        when (currentState) {
            FacePositioningUiState.Ready -> navigator.navigateTo(KioskDestination.Processing)
            FacePositioningUiState.NoFaceDetected -> applyState(FacePositioningUiState.NoFaceDetected)
            FacePositioningUiState.MultipleFacesDetected -> applyState(FacePositioningUiState.MultipleFacesDetected)
            FacePositioningUiState.PermissionDenied,
            FacePositioningUiState.CameraUnavailable -> {
                Toast.makeText(context, R.string.scan_mock_retry_message, Toast.LENGTH_SHORT).show()
                applyState(FacePositioningUiState.Ready)
            }
        }
    }

    private fun applyState(state: FacePositioningUiState) {
        currentState = state
        when (state) {
            FacePositioningUiState.Ready -> showReadyState()
            FacePositioningUiState.NoFaceDetected -> showNoFaceState()
            FacePositioningUiState.MultipleFacesDetected -> showMultipleFacesState()
            FacePositioningUiState.PermissionDenied -> showPermissionIssueState()
            FacePositioningUiState.CameraUnavailable -> showCameraUnavailableState()
        }
    }

    private fun showReadyState() {
        binding.faceGuide.visibility = View.VISIBLE
        binding.cameraIssueText.visibility = View.GONE
        binding.cameraStatusText.setBackgroundResource(R.drawable.bg_status_ready_pill)
        binding.cameraStatusText.setTextColor(context.getColor(R.color.facefit_ink))
        binding.cameraStatusText.text = context.getString(R.string.scan_status_ready)
        binding.previewMessageText.text = context.getString(R.string.scan_preview_ready)
        binding.instructionBodyText.text = context.getString(R.string.scan_body)
        binding.tipText.text = context.getString(R.string.scan_tip_ready)
        binding.captureButton.text = context.getString(R.string.scan_capture_button)
        binding.captureButton.isEnabled = true
        bindBaseGuidance()
    }

    private fun showNoFaceState() {
        binding.faceGuide.visibility = View.VISIBLE
        binding.cameraIssueText.visibility = View.GONE
        binding.cameraStatusText.setBackgroundResource(R.drawable.bg_status_warning_pill)
        binding.cameraStatusText.setTextColor(context.getColor(R.color.facefit_ink))
        binding.cameraStatusText.text = context.getString(R.string.scan_status_no_face)
        binding.previewMessageText.text = context.getString(R.string.scan_preview_no_face)
        binding.instructionBodyText.text = context.getString(R.string.scan_body_no_face)
        binding.tipText.text = context.getString(R.string.scan_tip_no_face)
        binding.captureButton.text = context.getString(R.string.scan_capture_waiting_button)
        binding.captureButton.isEnabled = false
        bindRow(
            binding.faceCenteredRow,
            title = context.getString(R.string.scan_guidance_face_missing_title),
            body = context.getString(R.string.scan_guidance_face_missing_body)
        )
    }

    private fun showMultipleFacesState() {
        binding.faceGuide.visibility = View.VISIBLE
        binding.cameraIssueText.visibility = View.GONE
        binding.cameraStatusText.setBackgroundResource(R.drawable.bg_status_warning_pill)
        binding.cameraStatusText.setTextColor(context.getColor(R.color.facefit_ink))
        binding.cameraStatusText.text = context.getString(R.string.scan_status_multiple_faces)
        binding.previewMessageText.text = context.getString(R.string.scan_preview_multiple_faces)
        binding.instructionBodyText.text = context.getString(R.string.scan_body_multiple_faces)
        binding.tipText.text = context.getString(R.string.scan_tip_multiple_faces)
        binding.captureButton.text = context.getString(R.string.scan_capture_waiting_button)
        binding.captureButton.isEnabled = false
        bindRow(
            binding.faceCenteredRow,
            title = context.getString(R.string.scan_guidance_multiple_faces_title),
            body = context.getString(R.string.scan_guidance_multiple_faces_body)
        )
    }

    private fun showPermissionIssueState() {
        showCameraBlockedState(
            status = context.getString(R.string.scan_status_permission_denied),
            issue = context.getString(R.string.scan_issue_permission_denied),
            tip = context.getString(R.string.scan_tip_permission_denied)
        )
    }

    private fun showCameraUnavailableState() {
        showCameraBlockedState(
            status = context.getString(R.string.scan_status_camera_unavailable),
            issue = context.getString(R.string.scan_issue_camera_unavailable),
            tip = context.getString(R.string.scan_tip_camera_unavailable)
        )
    }

    private fun showCameraBlockedState(status: String, issue: String, tip: String) {
        binding.faceGuide.visibility = View.GONE
        binding.cameraIssueText.visibility = View.VISIBLE
        binding.cameraIssueText.text = issue
        binding.cameraStatusText.setBackgroundResource(R.drawable.bg_status_error_pill)
        binding.cameraStatusText.setTextColor(context.getColor(R.color.facefit_error))
        binding.cameraStatusText.text = status
        binding.previewMessageText.text = context.getString(R.string.scan_preview_camera_issue)
        binding.instructionBodyText.text = context.getString(R.string.scan_body_camera_issue)
        binding.tipText.text = tip
        binding.captureButton.text = context.getString(R.string.scan_retry_camera_button)
        binding.captureButton.isEnabled = true
    }

    private fun stateFromCameraReadiness(readiness: CameraReadiness): FacePositioningUiState {
        return when (readiness) {
            CameraReadiness.Ready -> FacePositioningUiState.Ready
            CameraReadiness.PermissionDenied -> FacePositioningUiState.PermissionDenied
            CameraReadiness.Unavailable -> FacePositioningUiState.CameraUnavailable
        }
    }

    private fun cycleMockStateForReview() {
        val states = listOf(
            FacePositioningUiState.Ready,
            FacePositioningUiState.NoFaceDetected,
            FacePositioningUiState.MultipleFacesDetected,
            FacePositioningUiState.PermissionDenied,
            FacePositioningUiState.CameraUnavailable
        )
        mockStateIndex = (mockStateIndex + 1) % states.size
        applyState(states[mockStateIndex])
    }
}

private sealed class FacePositioningUiState {
    data object Ready : FacePositioningUiState()
    data object NoFaceDetected : FacePositioningUiState()
    data object MultipleFacesDetected : FacePositioningUiState()
    data object PermissionDenied : FacePositioningUiState()
    data object CameraUnavailable : FacePositioningUiState()
}

