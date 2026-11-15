package com.example.facefit

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facefit.facefit.camera.TrackingBackend
import com.example.facefit.ui.camera.VtoPreviewFaceTracker
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test

/** Physical-device native regression: each delegate must leave borrowed pixels alive. */
class VtoTrackerOwnershipTest {
    @Test fun closingEitherDelegateDoesNotRecycleTheNextDelegatesInput() {
        try { System.loadLibrary("mediapipe_tasks_vision_jni") }
        catch(unsupported: UnsatisfiedLinkError) { Assume.assumeNoException("No native MediaPipe for this emulator ABI",unsupported) }
        val worker=Executors.newSingleThreadExecutor()
        try {
            worker.submit {
                val bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
                try {
                    for(backend in listOf(TrackingBackend.CPU,TrackingBackend.GPU)) {
                        val tracker=try { VtoPreviewFaceTracker(InstrumentationRegistry.getInstrumentation().targetContext,backend) }
                            catch(unsupported: Exception) { if(backend==TrackingBackend.GPU) continue else throw unsupported }
                        tracker.use {
                            it.warmUp()
                            for(i in 1..3) { it.detect(bitmap,android.os.SystemClock.uptimeMillis()); assertFalse(bitmap.isRecycled) }
                        }
                        assertFalse(bitmap.isRecycled)
                    }
                } finally { bitmap.recycle() }
            }.get()
        } finally { worker.shutdown() }
    }
}
