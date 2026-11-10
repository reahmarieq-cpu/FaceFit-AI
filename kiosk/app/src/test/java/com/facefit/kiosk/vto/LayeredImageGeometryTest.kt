package com.facefit.kiosk.vto

import com.facefit.kiosk.vto.facefit.tryon.geometry.GlassesRig
import com.facefit.kiosk.vto.facefit.tryon.geometry.LayeredImageGeometry
import com.facefit.kiosk.vto.facefit.tryon.geometry.Vector3
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
class LayeredImageGeometryTest {
    private fun rig(yaw: Float=0f)=GlassesRig(Vector3(400f,500f,0f),
        Vector3(cos(yaw),0f,-sin(yaw)),Vector3(0f,1f,0f),Vector3(sin(yaw),0f,cos(yaw)),200f)
    @Test fun neutralProjectionPreservesOriginalImageCoordinates() {
        val p=LayeredImageGeometry.imagePoint(rig(),300f,200f,200f,150f,2f)!!
        assertEquals(600f,p.x,.001f);assertEquals(600f,p.y,.001f)
    }
    @Test fun turningChangesPerspectiveAndKeepsCenterAnchored() {
        for(yaw in listOf(-.6f,.6f)) {
            val r=rig(yaw)
            val center=LayeredImageGeometry.project(r,0f,0f)!!
            assertEquals(400f,center.x,0f);assertEquals(500f,center.y,0f)
            val left=LayeredImageGeometry.project(r,-200f,0f)!!
            val right=LayeredImageGeometry.project(r,200f,0f)!!
            assertTrue(right.x-left.x<400f)
            assertTrue(abs((center.x-left.x)-(right.x-center.x))>1f)
        }
    }
    @Test fun invalidProjectionIsRejected() {
        assertNull(LayeredImageGeometry.project(rig(),Float.NaN,0f))
        assertNull(LayeredImageGeometry.project(rig(),0f,0f,-2000f))
    }
}
