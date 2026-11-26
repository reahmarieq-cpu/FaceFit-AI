package com.example.facefit.facefit.tryon.geometry

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
        "frames/aviator/model.glb" -> ModelAnchors(
            Vector3(-.036817696f,-.010438301f,-.001024200f),
            Vector3(.036817696f,-.010438301f,-.001024200f))
        "frames/wayfarer/model.glb" -> ModelAnchors(
            Vector3(-.035410527f,-.011039601f,-.000928100f),
            Vector3(.035410527f,-.011039601f,-.000928100f))
        "frames/geometric/model.glb" -> ModelAnchors(
            Vector3(-.036991198f,-.008910201f,-.001006900f),
            Vector3(.036991198f,-.008910201f,-.001006900f))
        "frames/oval/model.glb" -> ModelAnchors(
            Vector3(-.035191555f,-.007830201f,-.000906000f),
            Vector3(.035191555f,-.007830201f,-.000906000f))
        "frames/oval/model1.glb" -> ModelAnchors(
            Vector3(-.036738995f,-.008527300f,-.000993400f),
            Vector3(.036738995f,-.008527300f,-.000993400f))
        "frames/browline/browline_crystal_frame_049.glb" -> ModelAnchors(
            Vector3(-.035270598f,-.009932201f,-.000933700f),
            Vector3(.035270598f,-.009932201f,-.000933700f))
        "frames/rectangle/rectangle_rose_gold_metal_frame_028.glb" -> ModelAnchors(
            Vector3(-.036232699f,-.009775400f,-.000996400f),
            Vector3(.036232699f,-.009775400f,-.000996400f))
        else -> null // Uncalibrated models retain their existing eye-based placement.
    }
}