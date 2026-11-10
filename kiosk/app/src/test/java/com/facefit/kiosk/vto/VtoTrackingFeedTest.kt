package com.facefit.kiosk.vto

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.ui.tryon.tracking.VtoTrackingFeed
import org.junit.Assert.*
import org.junit.Test

class VtoTrackingFeedTest {
    private fun face() = FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "", emptyList(), 512, 800, false)
    @Test fun stalledTrackingExpiresAndNewResultRecoversImmediately() {
        val feed = VtoTrackingFeed()
        val first = face()
        feed.publish(first, 1_000_000_000L)
        assertSame(first, feed.freshAt(1_200_000_000L))
        assertNull(feed.freshAt(1_251_000_000L))
        val next = face()
        feed.publish(next, 1_260_000_000L)
        assertSame(next, feed.freshAt(1_260_000_000L))
    }
    @Test fun noFaceClearsPreviouslyPublishedTracking() {
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
