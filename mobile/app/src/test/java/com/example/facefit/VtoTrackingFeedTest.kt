package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.ui.tryon.tracking.VtoTrackingFeed
import org.junit.Assert.*
import org.junit.Test

class VtoTrackingFeedTest {
    private fun face(dx: Float = 0f, dy: Float = 0f, bridgeShift: Float = 0f, halfSpan: Float = .15f) =
        FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "", List(478) { index ->
            FaceAnalyzer.LandmarkPoint(
                (.5f + if (index == 263 || index == 362) halfSpan else -halfSpan) + dx + if (index == 168) bridgeShift else 0f,
                .4f + dy, index / 10000f)
        }, 1000, 1000, true)

    @Test fun usesLatestDirectionWhenTurningAndReversing() {
        val feed = VtoTrackingFeed()
        feed.publish(face(), 0L, 300_000_000L)
        feed.publish(face(.02f), 40_000_000L, 340_000_000L)
        val turning = face(.025f, .02f, .02f)
        feed.publish(turning, 80_000_000L, 380_000_000L)
        val projectedTurn = requireNotNull(feed.displayPose(turning, 380_000_000L))
        assertTrue(projectedTurn.landmarks[33].y > turning.landmarks[33].y)

        val reversed = face(.02f, 0f)
        feed.publish(reversed, 120_000_000L, 420_000_000L)
        val projectedReversal = requireNotNull(feed.displayPose(reversed, 420_000_000L))
        assertTrue(projectedReversal.landmarks[33].y < reversed.landmarks[33].y)
    }

    @Test fun consistentTranslationUsesPipelineDelayAndStopsWithoutLead() {
        val feed = VtoTrackingFeed()
        feed.publish(face(), 0L, 300_000_000L)
        feed.publish(face(.02f), 40_000_000L, 340_000_000L)
        val moving = face(.04f)
        feed.publish(moving, 80_000_000L, 380_000_000L)
        val led = requireNotNull(feed.displayPose(feed.latestAt(380_000_000L), 380_000_000L))
        assertEquals(.008f, led.landmarks[33].x - moving.landmarks[33].x, .00001f)
        assertEquals(moving.landmarks[33].z, led.landmarks[33].z, 0f)
        val stopped = face(.04f)
        feed.publish(stopped, 120_000_000L, 420_000_000L)
        assertSame(stopped, feed.displayPose(feed.latestAt(420_000_000L), 420_000_000L))
    }
    @Test fun consistentTranslationUsesMeasuredAgeWithinLeadBounds() {
        for ((ageMs, expectedMs) in listOf(12L to 12L, 35L to 16L, 70L to 16L)) {
            val feed = VtoTrackingFeed()
            val age = ageMs * 1_000_000L
            feed.publish(face(), 0L, age)
            feed.publish(face(.02f), 40_000_000L, 40_000_000L + age)
            val moving = face(.04f)
            feed.publish(moving, 80_000_000L, 80_000_000L + age)
            val shown = requireNotNull(feed.displayPose(feed.latestAt(80_000_000L + age), 80_000_000L + age))
            assertEquals(.02f * expectedMs / 40f, shown.landmarks[33].x - moving.landmarks[33].x, .00001f)
        }
    }

    @Test fun predictsAtSlowMeshCadenceAndDiscardsLeadAfterAStop() {
        val feed = VtoTrackingFeed()
        feed.publish(face(halfSpan = .4f), 0L, 300_000_000L)
        feed.publish(face(.04f, halfSpan = .4f), 250_000_000L, 550_000_000L)
        val moving = face(.08f, halfSpan = .4f)
        feed.publish(moving, 500_000_000L, 800_000_000L)
        val led = requireNotNull(feed.displayPose(feed.latestAt(800_000_000L), 800_000_000L))
        assertEquals(.00256f, led.landmarks[33].x - moving.landmarks[33].x, .00001f)
        val stopped = face(.08f, halfSpan = .4f)
        feed.publish(stopped, 750_000_000L, 1_050_000_000L)
        assertSame(stopped, feed.displayPose(feed.latestAt(1_050_000_000L), 1_050_000_000L))
    }

    @Test fun tracksMotionBelowTheOldDeadbandAndRejectsRestingNoise() {
        val feed = VtoTrackingFeed()
        feed.publish(face(), 0L, 40_000_000L)
        val moving = face(.006f)
        feed.publish(moving, 40_000_000L, 80_000_000L)
        val projected = requireNotNull(feed.predictAt(80_000_000L))
        assertTrue(projected.landmarks[33].x > moving.landmarks[33].x)
        feed.publish(face(.005f), 80_000_000L, 120_000_000L)
        feed.publish(face(.007f), 120_000_000L, 160_000_000L)
        val resting = face(.005f)
        feed.publish(resting, 160_000_000L, 200_000_000L)
        assertSame(resting, feed.predictAt(200_000_000L))
    }

    @Test fun advancesOnVsyncWithoutANewMeasurement() {
        val feed = VtoTrackingFeed()
        feed.publish(face(), 0L, 240_000_000L)
        val lastMesh = face(.04f)
        feed.publish(lastMesh, 250_000_000L, 490_000_000L)
        val atDelivery = requireNotNull(feed.displayPose(lastMesh, 490_000_000L))
        val nextTick = requireNotNull(feed.displayPose(lastMesh, 506_000_000L))
        val effectiveStepMs = 0f
        assertEquals(.04f * effectiveStepMs / 250f,
            nextTick.landmarks[33].x - atDelivery.landmarks[33].x, .00001f)
        assertSame(lastMesh, feed.latestAt(506_000_000L))
    }

    @Test fun capsPredictionAtOneAndAHalfMeasuredPipelineDelays() {
        val feed = VtoTrackingFeed()
        feed.publish(face(), 0L, 175_000_000L)
        val moving = face(.058f)
        feed.publish(moving, 58_000_000L, 233_000_000L)
        val current = feed.predictAt(233_000_000L)!!
        val capped = feed.predictAt(380_000_000L)!!
        assertEquals(.016f, current.landmarks[33].x - moving.landmarks[33].x, .00001f)
        assertEquals(.016f, capped.landmarks[33].x - moving.landmarks[33].x, .00001f)
    }

    @Test fun holdsAnchorAfterFreshnessExpiresDuringFaceLossGrace() {
        val feed = VtoTrackingFeed()
        feed.publish(face(), 0L, 300_000_000L)
        feed.publish(face(.04f), 250_000_000L, 550_000_000L)

        assertNull(feed.predictAt(1_150_000_000L))
        val atExpiry = requireNotNull(feed.predictAt(1_150_000_000L, holdAnchor = true))
        val later = requireNotNull(feed.predictAt(1_400_000_000L, holdAnchor = true))
        val coast = later.landmarks[33].x - atExpiry.landmarks[33].x
        assertTrue(coast >= 0f)
        assertTrue(coast < .04f)
    }

    @Test fun explicitLossPassesThroughAndAStalledValidResultExpires() {
        val feed = VtoTrackingFeed()
        val face = face()
        feed.publish(face, 0L, 40_000_000L)
        assertSame(face, feed.latestAt(119_000_000L))
        assertNull(feed.latestAt(120_000_000L))

        val loss = face.copy(isFaceDetected = false)
        feed.publish(loss, 160_000_000L, 200_000_000L)
        assertSame(loss, feed.displayPose(feed.latestAt(200_000_000L), 200_000_000L))
    }
}
