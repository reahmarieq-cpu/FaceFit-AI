package com.example.facefit.ui.tryon.rendering

import com.example.facefit.facefit.tryon.tracking.VtoFrameShiftTracker
import kotlin.math.hypot

/**
 * Moves a mesh-based glasses pose by how both eyes moved since the mesh's camera frame:
 * translation of the point between the eyes, in-plane rotation of the eye line, and uniform
 * scale from the change in eye distance. Turning (yaw) and nodding (pitch) still come from the mesh.
 */
internal object VtoShiftedPose {
    /** Maps the shift's eye points into the preview, then [applyPreview]. [points] is reusable scratch (8 floats). */
    fun apply(mesh: FloatArray, shift: VtoFrameShiftTracker.Shift, toPreview: android.graphics.Matrix,
        viewHeight: Float, points: FloatArray, out: FloatArray): Boolean {
        points[0] = shift.firstFromX; points[1] = shift.firstFromY
        points[2] = shift.secondFromX; points[3] = shift.secondFromY
        points[4] = shift.firstToX; points[5] = shift.firstToY
        points[6] = shift.secondToX; points[7] = shift.secondToY
        toPreview.mapPoints(points)
        return applyPreview(mesh, points, viewHeight, out)
    }

    /**
     * [p] holds first-eye-from, second-eye-from, first-eye-to, second-eye-to in preview pixels
     * (y down). [mesh] and [out] are column-major 4x4 matrices in the renderer's world (y up).
     * Returns false, leaving [out] unusable, when the eye points are degenerate.
     */
    fun applyPreview(mesh: FloatArray, p: FloatArray, viewHeight: Float, out: FloatArray): Boolean {
        val ax = p[0]; val ay = viewHeight - p[1]
        val bx = p[2]; val by = viewHeight - p[3]
        val cx = p[4]; val cy = viewHeight - p[5]
        val dx = p[6]; val dy = viewHeight - p[7]
        val v0x = bx - ax; val v0y = by - ay
        val v1x = dx - cx; val v1y = dy - cy
        val l0 = hypot(v0x, v0y)
        val l1 = hypot(v1x, v1y)
        if (!(l0 > 1e-3f) || !(l1 > 1e-3f) || !l0.isFinite() || !l1.isFinite()) return false
        val s = l1 / l0
        val cos = (v0x * v1x + v0y * v1y) / (l0 * l1)
        val sin = (v0x * v1y - v0y * v1x) / (l0 * l1)
        val m0x = (ax + bx) * .5f; val m0y = (ay + by) * .5f
        val m1x = (cx + dx) * .5f; val m1y = (cy + dy) * .5f
        for (i in 0..2) {
            val x = mesh[i * 4]; val y = mesh[i * 4 + 1]
            out[i * 4] = s * (cos * x - sin * y)
            out[i * 4 + 1] = s * (sin * x + cos * y)
            out[i * 4 + 2] = s * mesh[i * 4 + 2]
            out[i * 4 + 3] = mesh[i * 4 + 3]
        }
        val ox = mesh[12] - m0x
        val oy = mesh[13] - m0y
        out[12] = m1x + s * (cos * ox - sin * oy)
        out[13] = m1y + s * (sin * ox + cos * oy)
        out[14] = mesh[14]
        out[15] = mesh[15]
        return out.all { it.isFinite() }
    }
}
