package com.example.facefit

import android.app.Application
import com.example.facefit.data.privacy.PrivacyRepository

class PrivacyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PrivacyRepository(this).applyAnalyticsPreference()
    }
}
