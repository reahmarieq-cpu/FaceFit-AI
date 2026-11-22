package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.analysis.FacialGeometry
import com.example.facefit.facefit.analysis.FaceScanSamples
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FacialGeometryTest {
    private fun outline(square: Boolean, width: Int = 800, height: Int = 1200, rotation: Float = 0f): List<FaceAnalyzer.LandmarkPoint> {
        val coordinates = mapOf(
            10 to (0f to -90f), 152 to (0f to 150f),
            234 to (-100f to 0f), 454 to (100f to 0f),
            103 to (-90f to -65f), 332 to (90f to -65f),
            172 to (if (square) -88f to 90f else -76f to 70f),
            397 to (if (square) 88f to 90f else 76f to 70f),
            136 to (-50f to 115f), 365 to (50f to 115f)
        )
        return List(478) { index ->
            val (x, y) = coordinates[index] ?: (0f to 0f)
            FaceAnalyzer.LandmarkPoint(
                (400f + x * cos(rotation) - y * sin(rotation)) / width,
                (500f + x * sin(rotation) + y * cos(rotation)) / height
            )
        }
    }

    @Test fun sameHeightAndWidthCanHaveDifferentJawMeasurements() {
        val round = FacialGeometry.measure(outline(false), 800, 1200)!!
        val square = FacialGeometry.measure(outline(true), 800, 1200)!!
        assertEquals(round.lengthToWidth, square.lengthToWidth, 0.0001f)
        assertTrue(square.jawToCheek > round.jawToCheek)
        assertTrue(square.jawAngle < round.jawAngle)
    }

    @Test fun imageAspectRatioRollAndMirroringDoNotChangeMeasurements() {
        val expected = FacialGeometry.measure(outline(true), 800, 1200)!!
        val rotated = outline(true, 1200, 800, 0.3f).map { it.copy(x = 1f - it.x) }
        val actual = FacialGeometry.measure(rotated, 1200, 800)!!
        assertEquals(expected.lengthToWidth, actual.lengthToWidth, 0.0001f)
        assertEquals(expected.jawToCheek, actual.jawToCheek, 0.0001f)
        assertEquals(expected.jawAngle, actual.jawAngle, 0.001f)
    }

    @Test fun missingInvalidOrCollapsedPointsDoNotProduceAShape() {
        assertNull(FacialGeometry.measure(emptyList(), 800, 1200))
        assertNull(FacialGeometry.measure(outline(false), 0, 1200))
        val collapsed = List(478) { FaceAnalyzer.LandmarkPoint(0.5f, 0.5f) }
        assertNull(FacialGeometry.measure(collapsed, 800, 1200))
        val invalid = outline(false).toMutableList().apply { this[172] = this[172].copy(x = Float.NaN) }
        assertNull(FacialGeometry.measure(invalid, 800, 1200))
    }

    @Test fun medianResistsAnOutlierWithoutReportingProbability() {
        val m = FacialGeometry.Measurements(1.20f, 0.90f, 0.76f, 0.59f, 155f)
        val result = FacialGeometry.median(listOf(m, m, m.copy(lengthToWidth = 1.8f)))!!
        assertEquals(m, result)
    }

    private fun metrics(square: Boolean = false): FaceAnalyzer.FaceMetrics {
        val points = outline(square)
        val shape = FacialGeometry.measure(points, 800, 1200)!!
        return FaceAnalyzer.FaceMetrics(0.9f, shape.jawToCheek, 0.25f,
            "", points, 800, 1200, true, shape,
            stabilityFeatures = listOf(shape.lengthToWidth, 0.9f, shape.jawToCheek,
                0.5f, 0.3f, 0.6f, 1.05f))
    }

    @Test fun captureRequiresFifteenFreshFramesAndTwoSeconds() {
        val collector = FaceScanSamples()
        for (time in (0L..13L).map { it * 150L }) {
            assertNull(collector.add(metrics(), true, time).captured)
        }
        val captured = collector.add(metrics(), true, 2100L).captured!!
        assertEquals(15, captured.shapeSampleCount)
        assertEquals(15, captured.shapeFeatureSamples.size)
        assertEquals("", captured.faceShape)
        assertEquals(0.76f, captured.stabilityFeatures[2], 0.001f)
        val tooFast = FaceScanSamples()
        repeat(10) { assertNull(tooFast.add(metrics(), true, it * 20L).captured) }
    }

    @Test fun losingAlignmentOrReceivingStaleFramesClearsPreviousSamples() {
        val collector = FaceScanSamples()
        repeat(14) { collector.add(metrics(), true, it * 150L) }
        assertEquals(0f, collector.add(metrics(), false, 2100L).progress, 0f)
        assertNull(collector.add(metrics(true), true, 2250L).captured)
        assertNull(collector.add(metrics(true), true, 3400L).captured)
        repeat(14) { i ->
            val result = collector.add(metrics(true), true, 3550L + i * 150L)
            if (i < 13) assertNull(result.captured)
            else assertEquals(0.88f, result.captured!!.stabilityFeatures[2], 0.001f)
        }
    }

    @Test fun invalidModelFeaturesResetCaptureAndAllSevenColumnsAreAggregated() {
        val collector = FaceScanSamples()
        repeat(14) { collector.add(metrics(), true, it * 150L) }
        assertEquals(0f, collector.add(metrics().copy(stabilityFeatures = emptyList()), true, 2100L).progress, 0f)
        repeat(15) { i ->
            val sample = metrics().let { m -> m.copy(stabilityFeatures = m.stabilityFeatures.mapIndexed { index, value ->
                if (index == 6 && i == 14) 1.20f else value
            }) }
            val update = collector.add(sample, true, 2400L + i * 150L)
            assertNull(update.captured)
            if (i == 14) assertTrue(update.waitingForStability)
        }
        var recovered: FaceAnalyzer.FaceMetrics? = null
        for (i in 15..50) {
            recovered = collector.add(metrics(), true, 2400L + i * 150L).captured
            if (recovered != null) break
        }
        assertNotNull(recovered)
        assertEquals(1.05f, recovered!!.stabilityFeatures[6], .001f)
    }

    @Test fun driftingChinMeasurementsWaitForAStableWindow() {
        val collector = FaceScanSamples()
        var update = FaceScanSamples.Update(0f)
        repeat(15) { i ->
            val sample = metrics().let { m -> m.copy(stabilityFeatures = m.stabilityFeatures.mapIndexed { index, value ->
                if (index == 4) value + i * 0.003f else value
            }) }
            update = collector.add(sample, true, i * 150L)
            assertNull(update.captured)
        }
        assertTrue(update.waitingForStability)
        var recovered: FaceAnalyzer.FaceMetrics? = null
        for (i in 15..60) {
            val result = collector.add(metrics(), true, i * 150L)
            if (result.captured != null) { recovered = result.captured; break }
        }
        assertNotNull(recovered)
        assertTrue(recovered!!.shapeSampleCount >= 15)
    }

    @Test fun capturePreservesOriginalFeatureRowsForAgreement() {
        val collector = FaceScanSamples()
        val frames = List(15) { i ->
            val frame = metrics()
            frame.copy(stabilityFeatures = frame.stabilityFeatures.mapIndexed { index, value ->
                if (index == 3) value + i * .0005f else value
            })
        }
        val captured = frames.mapIndexed { index, frame ->
            collector.add(frame, true, index * 150L).captured
        }.last()!!
        assertEquals(frames.map { it.stabilityFeatures }, captured.shapeFeatureSamples)
        assertEquals(.5035f, captured.stabilityFeatures[3], .00001f)
        assertEquals(0f, collector.add(metrics().copy(isFaceDetected = false, detectedFaceCount = 2),
            true, 2400L).progress, 0f)
    }

    @Test fun cameraDistanceAndPositionMustRemainSteadyEvenWhenRatiosDoNotChange() {
        for (zooming in listOf(true, false)) {
            val collector = FaceScanSamples()
            var update = FaceScanSamples.Update(0f)
            repeat(15) { i ->
                val base = metrics()
                val points = base.landmarks.map {
                    if (zooming) it.copy(x = .5f + (it.x - .5f) * (1 + i * .005f),
                        y = .5f + (it.y - .5f) * (1 + i * .005f))
                    else it.copy(x = it.x + i * .003f)
                }
                update = collector.add(base.copy(landmarks = points), true, i * 150L)
                assertNull(update.captured)
            }
            assertTrue(update.waitingForStability)
        }
    }

    @Test fun changingPoseAndExposureMustNotCompleteCapture() {
        for (changingPose in listOf(true, false)) {
            val collector = FaceScanSamples()
            var update = FaceScanSamples.Update(0f)
            repeat(15) { i ->
                val sample = metrics().copy(
                    headPose = com.example.facefit.facefit.analysis.FaceScanPose.Angles(0f,
                        if (changingPose) i * .3f else 0f, 0f),
                    scanLighting = com.example.facefit.facefit.analysis.FaceScanLighting.Measurement(
                        if (changingPose) .4f else .3f + i * .007f, 0f, 0f, 0f)
                )
                update = collector.add(sample, true, i * 150L)
                assertNull(update.captured)
            }
            assertTrue(update.waitingForStability)
        }
    }

    // ── New tests for effectiveLength floor and camera-distance invariance ────

    /**
     * When landmark 10 (top of head) is pulled down by bangs, [browToChin * 1.18] must
     * serve as a minimum face length so the person is not incorrectly classified as Oblong.
     *
     * Coordinate setup:
     *   landmark 151 (glabella/brow centre) → y = -60 px
     *   landmark 152 (chin)                 → y = +150 px  → browToChin = 210 px
     *   landmark 10  (hairline)             → y = -50 px   → rawHeight = 200 px (bangs hiding 10 px)
     *
     *   effectiveLength = max(200, 210 × 1.18) = max(200, 247.8) = 247.8 px
     *   cheekWidth      = landmark 234 to 454  = 200 px
     *   expectedLW      = 247.8 / 200 ≈ 1.239
     */
    @Test fun effectiveLengthFloorProtectsAgainstBangsHidingHairline() {
        // Build a minimal 478-landmark list; only the indices we use need real values.
        val browY    = -60f   // landmark 151 — glabella
        val chinY    = 150f   // landmark 152
        val hairY    = -50f   // landmark 10  — deliberately too low (bangs)
        val cheekX   = 100f   // landmarks 234 / 454
        val w        = 400
        val h        = 600
        val cx       = w / 2f
        val cy       = h / 2f

        val coords = mapOf(
            10  to ( 0f to hairY),
            152 to ( 0f to chinY),
            151 to ( 0f to browY),
            103 to (-90f to -50f), 332 to (90f to -50f),
            234 to (-cheekX to 0f), 454 to (cheekX to 0f),
            172 to (-80f to 80f), 397 to (80f to 80f),
            136 to (-40f to 110f), 365 to (40f to 110f)
        )
        val lms = List(478) { i ->
            val (dx, dy) = coords[i] ?: (0f to 0f)
            FaceAnalyzer.LandmarkPoint((cx + dx) / w, (cy + dy) / h)
        }

        val m = FacialGeometry.measure(lms, w, h)
        assertNotNull("Measurement should succeed for valid landmark set", m)
        m!!

        // browToChin = 150 - (-60) = 210 px; effectiveLength = max(200, 247.8) = 247.8
        // cheekWidth = 200 px
        val browToChin   = chinY - browY          // 210
        val rawHeight    = chinY - hairY          // 200
        val effectiveLen = maxOf(rawHeight, browToChin * 1.18f)
        val cheekWidth   = cheekX * 2f            // 200
        val expectedLW   = effectiveLen / cheekWidth

        assertEquals("lengthToWidth must use the 1.18× floor when bangs hide landmark 10",
            expectedLW, m.lengthToWidth, 0.0005f)
        assertTrue("effectiveLength floor must produce lw > raw lw",
            m.lengthToWidth > rawHeight / cheekWidth)
    }

    @Test fun effectiveLengthDoesNotInflateNormalFace() {
        // When rawHeight > browToChin × 1.18, the floor must NOT raise lengthToWidth
        // i.e. the max() picks rawHeight, not the browToChin formula.
        val browY   = -70f    // landmark 151
        val chinY   = 150f    // landmark 152
        val hairY   = -120f   // landmark 10 — clearly above brow (no bangs)
        val cheekX  = 100f
        val w       = 400
        val h       = 600
        val cx      = w / 2f
        val cy      = h / 2f

        val coords = mapOf(
            10  to ( 0f to hairY),
            152 to ( 0f to chinY),
            151 to ( 0f to browY),
            103 to (-90f to -60f), 332 to (90f to -60f),
            234 to (-cheekX to 0f), 454 to (cheekX to 0f),
            172 to (-80f to 80f), 397 to (80f to 80f),
            136 to (-40f to 110f), 365 to (40f to 110f)
        )
        val lms = List(478) { i ->
            val (dx, dy) = coords[i] ?: (0f to 0f)
            FaceAnalyzer.LandmarkPoint((cx + dx) / w, (cy + dy) / h)
        }

        val m = FacialGeometry.measure(lms, w, h)
        assertNotNull(m)
        m!!

        val rawHeight  = chinY - hairY      // 270
        val cheekWidth = cheekX * 2f        // 200
        val expectedLW = rawHeight / cheekWidth   // 1.35

        assertEquals("effectiveLength must equal rawHeight when hairline is clearly visible",
            expectedLW, m.lengthToWidth, 0.0005f)
    }

    @Test fun ratiosMustBeInvariantToCameraDistance() {
        // Scaling ALL landmarks by factor k relative to image centre must not change any ratio.
        val base = FacialGeometry.measure(outline(false), 800, 1200)!!
        // Zoom in: scale landmarks so face appears 20% larger on screen
        val zoomed = outline(false, 800, 1200).map { lm ->
            val k = 1.20f
            FaceAnalyzer.LandmarkPoint(
                0.5f + (lm.x - 0.5f) * k,
                0.5f + (lm.y - 0.5f) * k
            )
        }
        val scaled = FacialGeometry.measure(zoomed, 800, 1200)!!
        assertEquals("lengthToWidth must be scale-invariant",
            base.lengthToWidth,   scaled.lengthToWidth,   0.0001f)
        assertEquals("foreheadToCheek must be scale-invariant",
            base.foreheadToCheek, scaled.foreheadToCheek, 0.0001f)
        assertEquals("jawToCheek must be scale-invariant",
            base.jawToCheek,      scaled.jawToCheek,      0.0001f)
        assertEquals("jawAngle must be scale-invariant",
            base.jawAngle,        scaled.jawAngle,         0.001f)
    }
}
