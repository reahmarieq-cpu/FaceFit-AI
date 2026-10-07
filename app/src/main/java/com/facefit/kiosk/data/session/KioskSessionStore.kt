package com.facefit.kiosk.data.session

import com.facefit.kiosk.model.FrameRecommendation
import com.facefit.kiosk.model.ShopAccount
import java.io.File

interface KioskSessionStore {
    var activeShop: ShopAccount?
    var selectedRecommendation: FrameRecommendation?
    var capturedFaceImagePath: String?

    fun clearCustomerSession()
    fun clearAll()
}

class InMemoryKioskSessionStore : KioskSessionStore {
    override var activeShop: ShopAccount? = null
    override var selectedRecommendation: FrameRecommendation? = null
    override var capturedFaceImagePath: String? = null

    override fun clearCustomerSession() {
        selectedRecommendation = null
        capturedFaceImagePath?.let { File(it).delete() }
        capturedFaceImagePath = null
    }

    override fun clearAll() {
        activeShop = null
        clearCustomerSession()
    }
}
