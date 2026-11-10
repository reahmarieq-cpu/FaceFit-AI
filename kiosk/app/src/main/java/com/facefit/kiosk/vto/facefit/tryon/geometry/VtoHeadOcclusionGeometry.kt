package com.facefit.kiosk.vto.facefit.tryon.geometry

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.tryon.tracking.VtoHeadPose

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Tracked facial surface plus a pose-aligned, approximate skull behind its oval. */
internal object VtoHeadOcclusionGeometry {
    val oval = intArrayOf(10,338,297,332,284,251,389,356,454,323,361,288,397,365,379,378,400,377,152,148,176,149,150,136,172,58,132,93,234,127,162,21,54,103,67,109)
    private val holes = arrayOf(
        intArrayOf(33,7,163,144,145,153,154,155,133,173,157,158,159,160,161,246),
        intArrayOf(263,249,390,373,374,380,381,382,362,398,384,385,386,387,388,466),
        intArrayOf(78,95,88,178,87,14,317,402,318,324,308,415,310,311,312,13,82,81,80,191))
    const val vertexCount = 544

    fun indices(front: ByteArray): ByteArray {
        require(front.size % 6 == 0)
        val extra = ArrayList<Int>()
        fun triangle(a: Int,b: Int,c: Int) { extra.add(a); extra.add(b); extra.add(c) }
        for (i in oval.indices) {
            val j=(i+1)%oval.size
            triangle(oval[i],468+i,oval[j]); triangle(oval[j],468+i,468+j)
            triangle(468+i,504+i,468+j); triangle(468+j,504+i,504+j)
            triangle(504+i,540,504+j)
        }
        holes.forEachIndexed { h, ring ->
            for(i in ring.indices) triangle(ring[i],541+h,ring[(i+1)%ring.size])
        }
        return ByteBuffer.allocate(front.size+extra.size*2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(front); extra.forEach { putShort(it.toShort()) }
        }.array()
    }

    fun positions(face: FaceAnalyzer.FaceMetrics?, width: Int, height: Int, sharedRig: GlassesRig? = null): FloatArray? {
        if(face?.isFaceDetected != true || face.landmarks.size < 468 || width<=0 || height<=0) return null
        val points=face.landmarks.take(468).map { Vector3(it.x*width,it.y*height,it.z*width) }
        if(points.any { !it.isFinite() }) return null
        val first=(points[33]+points[133])*.5f
        val second=(points[263]+points[362])*.5f
        val rig=sharedRig ?: VtoHeadPose.rig(face,width,height,1f,0f) ?: return null
        val eyeCenter=(first+second)*.5f
        val headCenter=eyeCenter+rig.yAxis*((points[152]-points[10]).length()*.15f)
        val pd=(second-first).length()
        val headWidth=kotlin.math.abs((points[454]-points[234]).dot(rig.xAxis)).coerceIn(pd*1.5f,pd*2.6f)
        val all=points.toMutableList()
        // Keep the rear silhouette wide and deep enough to contain the full temple.
        // A narrow rear taper lets an already hidden arm emerge again as a floating tip.
        for(ring in 0..1) for(index in oval) {
            val d=points[index]-headCenter
            all.add(headCenter+rig.xAxis*(d.dot(rig.xAxis)*(if(ring==0) 1.02f else 1.02f))+
                rig.yAxis*(d.dot(rig.yAxis)*(if(ring==0) 1.02f else 1.02f))+
                rig.zAxis*(headWidth*(if(ring==0) .45f else 1.40f)))
        }
        all.add(headCenter+rig.zAxis*(headWidth*1.65f))
        holes.forEach { ring ->
            var center=Vector3(0f,0f,0f)
            ring.forEach { center=center+points[it] }
            all.add(center*(1f/ring.size))
        }
        return FloatArray(vertexCount*3).also { result ->
            all.forEachIndexed { i,p -> result[i*3]=p.x; result[i*3+1]=height-p.y; result[i*3+2]=-2000f-p.z }
        }
    }
}
