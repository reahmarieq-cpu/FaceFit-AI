package com.example.facefit.logic

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Experimental LAN-only FaceNet API pilot.
 *
 * Emulator: 10.0.2.2 reaches the development PC.
 * Real phone: replace with your development PC's private Wi-Fi IPv4 address.
 * Never put an unauthenticated HTTP image-upload endpoint on the public Internet.
 */
object FaceNetPilotConfig {
    const val API_BASE_URL = "http://10.0.2.2:8000"

    /** Production/release builds keep the existing local geometric classifier. */
    fun isEnabled(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
