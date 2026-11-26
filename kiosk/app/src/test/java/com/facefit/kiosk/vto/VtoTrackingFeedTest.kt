package com.facefit.kiosk.vto

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.ui.tryon.tracking.VtoTrackingFeed
import org.junit.Assert.*
import org.junit.Test

class VtoTrackingFeedTest {
    private fun face(detected: Boolean = true) = FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "",
        if (detected) List(468) { FaceAnalyzer.LandmarkPoint(.5f, .5f) } else emptyList(),
        512, 800, detected)
    @Test fun stalledTrackingExpiresAndNewResultRecoversImmediately() {
        val feed = VtoTrackingFeed()
        val first = face()
        feed.publish(first, 1_000_000_000L)
        assertSame(first, feed.freshAt(1_200_000_000L))
        assertNull(feed.freshAt(1_350_000_000L))
        val next = face()
        feed.publish(next, 1_360_000_000L)
        assertSame(next, feed.freshAt(1_360_000_000L))
    }
    @Test fun consecutiveMissesHoldPoseForOnlyOneGracePeriod() {
        val feed = VtoTrackingFeed()
        val tracked = face()
        feed.publish(tracked, 0L)
        val miss = face(false)
        feed.publish(miss, 33_000_000L)
        assertSame(miss, feed.latest)
        assertSame(tracked, feed.freshAt(33_000_000L))
        feed.publish(face(false), 66_000_000L)
        assertSame(tracked, feed.freshAt(282_999_999L))
        assertNull(feed.freshAt(283_000_000L))
        val recovered = face()
        feed.publish(recovered, 290_000_000L)
        assertSame(recovered, feed.freshAt(290_000_000L))
    }

    @Test fun explicitClearDiscardsPoseImmediately() {
        val feed = VtoTrackingFeed()
        feed.publish(face(), 0L)
        feed.publish(null, 16_000_000L)
        assertNull(feed.freshAt(16_000_000L))
    }
    @Test fun resultArrivingAfterVsyncTimestampRemainsFresh() {
        val feed = VtoTrackingFeed()
        val next = face()
        feed.publish(next, 20_000_000L)
        assertSame(next, feed.freshAt(16_000_000L))
    }
}
