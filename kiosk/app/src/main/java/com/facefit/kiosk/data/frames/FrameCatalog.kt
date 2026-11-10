package com.facefit.kiosk.data.frames

import com.facefit.kiosk.model.FaceScanResult
import com.facefit.kiosk.model.FrameRecommendation

/** Called on a worker thread. Firebase/retailer adapter must enforce shop authorization. */
interface FrameCatalog {
    fun recommendationsFor(profile: FaceScanResult, shopId: String): List<FrameRecommendation>
}