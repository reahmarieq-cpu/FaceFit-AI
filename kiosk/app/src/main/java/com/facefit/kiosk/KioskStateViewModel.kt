package com.facefit.kiosk

import androidx.lifecycle.ViewModel
import com.facefit.kiosk.data.session.InMemoryKioskSessionStore
import com.facefit.kiosk.navigation.KioskDestination

/** Retains the kiosk session across orientation and window size changes. */
class KioskStateViewModel : ViewModel() {
    val sessionStore = InMemoryKioskSessionStore()
    var destination: KioskDestination = KioskDestination.ShopLogin

    override fun onCleared() {
        sessionStore.clearAll()
    }
}
