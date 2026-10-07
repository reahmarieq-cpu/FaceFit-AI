package com.facefit.kiosk.data.session

import com.facefit.kiosk.model.FrameRecommendation
import com.facefit.kiosk.model.ShopAccount

interface KioskSessionStore {
    var activeShop: ShopAccount?
    var selectedRecommendation: FrameRecommendation?

    fun clearCustomerSession()
    fun clearAll()
}

class InMemoryKioskSessionStore : KioskSessionStore {
    override var activeShop: ShopAccount? = null
    override var selectedRecommendation: FrameRecommendation? = null

    override fun clearCustomerSession() {
        selectedRecommendation = null
    }

    override fun clearAll() {
        activeShop = null
        clearCustomerSession()
    }
}

