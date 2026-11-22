package com.example.facefit

import com.example.facefit.facefit.tryon.tracking.VtoHeadPose
import com.example.facefit.facefit.tryon.tracking.VtoRigResolver
import org.junit.Assert.*
import org.junit.Test

class VtoRigResolverTest {
    @Test fun resolverReturnsExactlyTheCurrentAnatomicalRig() {
        val resolver = VtoRigResolver()
        val face = anatomicalFace()
        assertEquals(VtoHeadPose.rig(face,1000,1600,1f,0f),
            resolver.resolve(face,1000,1600,1f,0f,0L))
        val moved = face.copy(landmarks=face.landmarks.map { it.copy(x=it.x+.02f) })
        assertEquals(VtoHeadPose.rig(moved,1000,1600,1f,0f),
            resolver.resolve(moved,1000,1600,1f,0f,16_000_000L))
    }
    @Test fun lossResetAndReacquisitionNeverBlendFromAnOlderOrigin() {
        val resolver = VtoRigResolver()
        val first = anatomicalFace()
        resolver.resolve(first,1000,1600,1f,0f,0L)
        assertNull(resolver.resolve(null,1000,1600,1f,0f,16_000_000L))
        resolver.reset()
        val moved = first.copy(landmarks=first.landmarks.map { it.copy(y=it.y+.1f) })
        assertEquals(VtoHeadPose.rig(moved,1000,1600,1f,0f),
            resolver.resolve(moved,1000,1600,1f,0f,32_000_000L))
    }
}
