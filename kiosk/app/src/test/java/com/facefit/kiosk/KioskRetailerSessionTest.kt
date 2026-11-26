package com.facefit.kiosk

import com.facefit.kiosk.data.auth.isEligibleRetailer
import com.facefit.kiosk.data.session.InMemoryKioskSessionStore
import com.facefit.kiosk.model.FaceScanResult
import com.facefit.kiosk.model.FaceShape
import com.facefit.kiosk.model.ShopAccount
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class KioskRetailerSessionTest {
    @Test fun eligibilityRequiresTheApprovedOwnerAndActiveKioskPlan() {
        assertTrue(isEligibleRetailer("owner", "owner", "approved", "kioskplan", "active"))
        assertFalse(isEligibleRetailer("owner", "other", "approved", "kioskplan", "active"))
        assertFalse(isEligibleRetailer("owner", "owner", "pending", "kioskplan", "active"))
        assertFalse(isEligibleRetailer("owner", "owner", "rejected", "kioskplan", "active"))
        assertFalse(isEligibleRetailer("owner", "owner", "approved", "kioskplan", "inactive"))
        assertFalse(isEligibleRetailer("owner", "owner", "approved", "other", "active"))
    }

    @Test fun customerResetDeletesCaptureAndPreservesTheRetailerAcrossSessions() {
        val store = InMemoryKioskSessionStore()
        val retailer = ShopAccount("owner", "Optical", "owner@example.com")
        store.activeShop = retailer
        repeat(2) {
            val capture = File.createTempFile("kiosk-test-capture", ".jpg")
            try {
                store.capturedFaceImagePath = capture.absolutePath
                store.analysisResult = FaceScanResult(FaceShape.Round)
                store.clearCustomerSession()
                assertSame(retailer, store.activeShop)
                assertFalse(capture.exists())
                assertNull(store.capturedFaceImagePath)
                assertNull(store.analysisResult)
                assertNull(store.selectedRecommendation)
                assertNull(store.capturedScanMetrics)
                assertNull(store.faceLandmarkScan)
                assertNull(store.facialFeatureMetrics)
                assertNull(store.latestFaceAnalysisResult)
            } finally {
                capture.delete()
            }
        }
        store.clearAll()
        assertNull(store.activeShop)
    }
}
