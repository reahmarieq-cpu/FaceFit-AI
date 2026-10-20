package com.facefit.kiosk.navigation

sealed class KioskDestination(val routeName: String) {
    data object ShopLogin : KioskDestination("shop_login")
    data object Welcome : KioskDestination("welcome")
    data object Consent : KioskDestination("consent")
    data object FacePositioning : KioskDestination("face_positioning")
    data object Processing : KioskDestination("processing")
    data object Results : KioskDestination("results")
    data object FrameDetails : KioskDestination("frame_details")
    data object SelectionConfirmation : KioskDestination("selection_confirmation")
    data object SessionComplete : KioskDestination("session_complete")
}

