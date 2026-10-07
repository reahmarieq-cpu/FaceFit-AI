package com.facefit.kiosk.data.session

import com.facefit.kiosk.model.FrameRecommendation
import com.facefit.kiosk.model.FaceLandmarkScan
import com.facefit.kiosk.model.ShopAccount
import com.facefit.kiosk.facefit.analysis.FaceAnalysisResult
import java.io.File

interface KioskSessionStore {
    var activeShop: ShopAccount?
    var selectedRecommendation: FrameRecommendation?
    var capturedFaceImagePath: String?
    var faceLandmarkScan: FaceLandmarkScan?
    var latestFaceAnalysisResult: FaceAnalysisResult?

    fun clearCustomerSession()
    fun clearAll()
}

class InMemoryKioskSessionStore : KioskSessionStore {
    override var activeShop: ShopAccount? = null
    override var selectedRecommendation: FrameRecommendation? = null
    override var capturedFaceImagePath: String? = null
    override var faceLandmarkScan: FaceLandmarkScan? = null
    override var latestFaceAnalysisResult: FaceAnalysisResult? = null

    override fun clearCustomerSession() {
        selectedRecommendation = null
        faceLandmarkScan = null
        latestFaceAnalysisResult = null
        capturedFaceImagePath?.let { File(it).delete() }
        capturedFaceImagePath = null
    }

    override fun clearAll() {
        activeShop = null
        clearCustomerSession()
    }
}
