package com.example.facefit.ui.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.SystemClock
import android.util.Log
import kotlin.math.sqrt

/** Runs on CameraX's capture callback thread and logs once per sensor second. */
internal class VtoCameraCadence(private val loggingEnabled: Boolean) : CameraCaptureSession.CaptureCallback() {
    private var windowStart = 0L
    private var firstFrame = 0L
    private val exposures = VtoExposureTelemetry()

    fun snrFactor(sensorTimestamp: Long?, captureElapsedNs: Long): Float =
        exposures.factor(sensorTimestamp, captureElapsedNs)

    override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult,
    ) {
        val callbackNs = SystemClock.elapsedRealtimeNanos()
        val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        val durationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION)
        exposures.record(timestamp, callbackNs, iso, exposureNs, aeState, durationNs)
        if (!loggingEnabled) return
        if (windowStart == 0L || timestamp <= windowStart || result.frameNumber < firstFrame) {
            windowStart = timestamp
            firstFrame = result.frameNumber
            return
        }
        val elapsed = timestamp - windowStart
        if (elapsed < 1_000_000_000L) return
        val frames = result.frameNumber - firstFrame
        val fps = frames * 1_000_000_000.0 / elapsed
        val durationMs = durationNs?.div(1_000_000.0)
        val exposureMs = exposureNs?.div(1_000_000.0)
        val aeRange = result.get(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE)
            ?: request.get(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)
        Log.i("FaceFitVTOTiming",
            "event=cameraCadence sensorFps=$fps frameDurationMs=$durationMs exposureMs=$exposureMs iso=$iso aeState=$aeState aeTargetFps=$aeRange")
        windowStart = timestamp
        firstFrame = result.frameNumber
    }

    fun logAnalyzerEntry(
        sensorNs: Long, analyzerNs: Long, ageMs: Double?, busyDrops: Int, copyMs: Long,
        windowNs: Long, acceptedFrames: Int, dropsSinceAccepted: Int, acceptedGapNs: Long?,
    ) {
        if (!loggingEnabled) return
        val resultNs = exposures.callbackTime(sensorNs)
        val resultToAnalyzerMs = resultNs?.let { (analyzerNs - it) / 1_000_000.0 }
        val sensorToResultMs = resultNs?.takeIf { ageMs != null && it >= sensorNs }
            ?.let { (it - sensorNs) / 1_000_000.0 }
        Log.i("FaceFitVTOTiming",
            "event=analyzerIntake sensorTimestampNs=$sensorNs sensorAgeMs=$ageMs " +
                "sensorToResultCallbackMs=$sensorToResultMs resultCallbackToAnalyzerMs=$resultToAnalyzerMs " +
                "windowMs=${windowNs / 1_000_000.0} acceptedFramesInWindow=$acceptedFrames " +
                "busyDropsInWindow=$busyDrops busyDropsSincePreviousAccepted=$dropsSinceAccepted " +
                "acceptedGapMs=${acceptedGapNs?.div(1_000_000.0)} copyAndCloseMs=$copyMs")
    }
}

/** Small, synchronized capture-result history keyed in the sensor clock domain. */
internal class VtoExposureTelemetry {
    private data class Exposure(val callbackNs: Long, val iso: Int?, val exposureNs: Long?,
        val aeState: Int?, val periodNs: Long?)
    private val samples = LinkedHashMap<Long, Exposure>()
    private var minimumIso: Int? = null

    @Synchronized
    fun record(sensorNs: Long, callbackNs: Long, iso: Int?, exposureNs: Long?, aeState: Int?, periodNs: Long?) {
        val validIso = iso?.takeIf { it > 0 }
        if (validIso != null) minimumIso = minOf(minimumIso ?: validIso, validIso)
        samples[sensorNs] = Exposure(callbackNs, validIso, exposureNs, aeState, periodNs)
        if (samples.size > CAPTURE_HISTORY) samples.remove(samples.keys.first())
    }

    @Synchronized
    fun callbackTime(sensorNs: Long): Long? = samples[sensorNs]?.callbackNs

    @Synchronized
    fun factor(sensorNs: Long?, captureElapsedNs: Long): Float {
        val baseline = minimumIso ?: return 1f
        val exposure = if (sensorNs != null) {
            samples[sensorNs] ?: samples.entries.lastOrNull { (stamp, value) ->
                val age = sensorNs - stamp
                age >= 0 && value.periodNs?.let { age <= it } == true
            }?.value
        } else {
            // Preview readback lacks a sensor id. Use only recent metadata already delivered.
            samples.values.lastOrNull {
                val age = captureElapsedNs - it.callbackNs
                age >= 0 && it.periodNs?.let { period -> age <= period } == true
            }
        }
        val iso = exposure?.iso ?: return 1f
        return sqrt((iso.toDouble() / baseline).coerceAtLeast(1.0)).toFloat()
    }

    private companion object {
        const val CAPTURE_HISTORY = 64
    }
}
