package com.facefit.kiosk.facefit.analysis

import com.facefit.kiosk.model.FaceLandmarkScan
import com.facefit.kiosk.model.FaceScanResult

/** Called on a worker thread. Implement this contract for Kenn's analyzer. */
interface KioskAnalyzer {
    fun analyze(capturedImagePath: String): KioskAnalysisOutcome
}

sealed class KioskAnalysisOutcome {
    data class Success(
        val profile: FaceScanResult,
        val landmarks: FaceLandmarkScan? = null
    ) : KioskAnalysisOutcome()
    data object NoFace : KioskAnalysisOutcome()
    data object MultipleFaces : KioskAnalysisOutcome()
    data object Failure : KioskAnalysisOutcome()
}