package com.facefit.kiosk.vto.facefit.tryon.geometry

/** Lightweight image-plane projection; no GLB generation or recoloring of product pixels. */
internal object LayeredImageGeometry {
    fun project(rig: GlassesRig,x: Float,y: Float,z: Float=0f): Vector3? {
        val delta=rig.xAxis*x+rig.yAxis*y+rig.zAxis*z
        val focal=rig.size*7f
        val divisor=focal+delta.z
        if(!divisor.isFinite() || divisor<=focal*.2f) return null
        val factor=focal/divisor
        return Vector3(rig.origin.x+delta.x*factor,rig.origin.y+delta.y*factor,delta.z)
            .takeIf { it.isFinite() }
    }
    fun imagePoint(rig: GlassesRig,x: Float,y: Float,centerX: Float,centerY: Float,unit: Float): Vector3? =
        project(rig,(x-centerX)*unit,(y-centerY)*unit)
}
