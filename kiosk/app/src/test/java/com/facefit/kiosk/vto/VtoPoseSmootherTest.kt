package com.facefit.kiosk.vto

import com.facefit.kiosk.vto.facefit.tryon.geometry.GlassesRig
import com.facefit.kiosk.vto.facefit.tryon.geometry.Vector3
import com.facefit.kiosk.vto.facefit.tryon.tracking.VtoPoseSmoother
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class VtoPoseSmootherTest {
    private fun rig(x: Float = 0f, yaw: Float = 0f) = GlassesRig(
        Vector3(x, 0f, 0f), Vector3(cos(yaw), 0f, -sin(yaw)), Vector3(0f, 1f, 0f),
        Vector3(sin(yaw), 0f, cos(yaw)), 100f
    )

    @Test fun firstPoseAndReacquisitionAreImmediate() {
        val smoother = VtoPoseSmoother()
        smoother.update(rig())
        assertEquals(0f, smoother.latest()!!.origin.x, .0001f)
        smoother.update(null)
        assertNull(smoother.latest())
        smoother.update(rig(100f))
        assertEquals(100f, smoother.latest()!!.origin.x, .0001f)
    }

    @Test fun restingDeadbandKeepsTheEntirePoseFrozen() {
        val smoother = VtoPoseSmoother()
        val initial = rig()
        smoother.update(initial)
        repeat(12) {
            smoother.update(rig(if (it % 2 == 0) 1f else -1f, .02f).copy(size = 101f))
            assertSame(initial, smoother.latest())
        }
    }

    @Test fun motionJustOutsideTheDeadbandEasesIn() {
        val smoother = VtoPoseSmoother()
        smoother.update(rig())
        smoother.update(rig(1.21f).copy(size = 110f))
        assertEquals(.1815f, smoother.latest()!!.origin.x, .001f)
        assertEquals(100.75f, smoother.latest()!!.size, .001f)
    }

    @Test fun largeTranslationSnapsOnTheFirstSample() {
        val smoother = VtoPoseSmoother()
        smoother.update(rig())
        val moved = rig(10f)
        smoother.update(moved)
        assertSame(moved, smoother.latest())
    }

    @Test fun widthStaysDampedEvenWhenRotationSnaps() {
        val smoother = VtoPoseSmoother()
        smoother.update(rig().copy(faceWidth = 200f))
        val target = rig(yaw = .5f).copy(faceWidth = 220f)
        smoother.update(target)
        assertEquals(target.zAxis, smoother.latest()!!.zAxis)
        assertEquals(201.6f, smoother.latest()!!.faceWidth, .001f)
    }

    @Test fun largeTurnsAtFixedNoseSnapAndSmallTurnsKeepOrthonormalAxes() {
        val smoother = VtoPoseSmoother()
        smoother.update(rig())
        smoother.update(rig(yaw = .15f))
        val blended = smoother.latest()!!
        assertTrue(blended.zAxis.x > 0f)
        assertEquals(1f, blended.zAxis.length(), .001f)
        assertEquals(0f, blended.xAxis.dot(blended.yAxis), .001f)
        val target = rig(yaw = .5f)
        smoother.update(target)
        assertSame(target, smoother.latest())
    }
}
