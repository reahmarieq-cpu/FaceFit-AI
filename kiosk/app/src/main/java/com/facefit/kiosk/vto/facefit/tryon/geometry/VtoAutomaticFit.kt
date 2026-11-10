package com.facefit.kiosk.vto.facefit.tryon.geometry

/** Per-model optical anchors: no universal face-shape multiplier or automatic vertical nudge. */
internal object VtoAutomaticFit {
    data class ModelAnchors(val firstLens: Vector3, val secondLens: Vector3) {
        val center get() = (firstLens + secondLens) * .5f
        val span get() = (secondLens - firstLens).length()
        fun isValid() = firstLens.isFinite() && secondLens.isFinite() && span > .001f
    }

    // Centroids of the two lens openings, measured from Frame_Front's projected triangles.
    // Z is the front surface. Native GLB geometry and all child-node transforms stay intact.
    val squareTortoise = ModelAnchors(
        Vector3(-35.85106f, -11.15299f, 2.75f),
        Vector3(35.68483f, -11.15299f, 2.75f))

    fun forAsset(path: String): ModelAnchors? = when (path) {
        "frames/SquareTortoise.glb", "frames/square_tortoise/SquareTortoise.glb" -> squareTortoise
        // Native named lens anchors supplied by the GLBs; these models use meters.
        "frames/cat-eye/model.glb" -> ModelAnchors(
            Vector3(-.036111798f,-.008126701f,-.000973600f),
            Vector3(.036111798f,-.008126701f,-.000973600f))
        "frames/Round/model.glb" -> ModelAnchors(
            Vector3(-.036751699f,-.008271000f,-.000996900f),
            Vector3(.036751699f,-.008271000f,-.000996900f))
        else -> null // Uncalibrated models retain their existing eye-based placement.
    }
}