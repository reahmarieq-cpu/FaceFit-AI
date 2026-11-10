package com.facefit.kiosk.vto.facefit.tryon.tracking

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.tryon.geometry.GlassesRig
import com.facefit.kiosk.vto.facefit.tryon.geometry.Vector3

/** VTO pose from a neutral 3D reference, instead of treating a curved face as a flat plane.
 * Reference points: MediaPipe v0.10.14 canonical_face_model.obj, Apache-2.0.
 * https://github.com/google-ai-edge/mediapipe/blob/v0.10.14/mediapipe/modules/face_geometry/data/canonical_face_model.obj
 */
internal object VtoHeadPose {
    val landmarkIndices = intArrayOf(10,33,133,263,362,168,6,197,127,356,234,454,152)
    // Screen coordinates: X right, Y down, Z behind the face.
    val reference = listOf(
        Vector3(0.000000f, -8.261778f, -4.481535f),
        Vector3(-4.445859f, -2.663991f, -3.173422f),
        Vector3(-1.856432f, -2.585245f, -3.757904f),
        Vector3(4.445859f, -2.663991f, -3.173422f),
        Vector3(1.856432f, -2.585245f, -3.757904f),
        Vector3(0.000000f, -3.271027f, -5.236015f),
        Vector3(0.000000f, -2.473255f, -5.788627f),
        Vector3(0.000000f, -1.728369f, -6.316750f),
        Vector3(-7.743095f, -2.364999f, 2.005167f),
        Vector3(7.743095f, -2.364999f, 2.005167f),
        Vector3(-7.664182f, -0.673132f, 2.435867f),
        Vector3(7.664182f, -0.673132f, 2.435867f),
        Vector3(0.000000f, 9.403378f, -4.264492f),
    )
    private val referenceCenter = reference.fold(Vector3(0f,0f,0f)) { a,b -> a+b }*(1f/reference.size)
    private val centeredReference = reference.map { it-referenceCenter }
    private val referenceInverse = requireNotNull(inverse(DoubleArray(9).also { covariance ->
        for(a in centeredReference) {
            val av=doubleArrayOf(a.x.toDouble(),a.y.toDouble(),a.z.toDouble())
            for(r in 0..2) for(c in 0..2) covariance[r*3+c]+=av[r]*av[c]
        }
    }))
    data class Axes(val right: Vector3, val down: Vector3, val behind: Vector3)

    fun fit(points: List<Vector3>): Axes? {
        if(points.size != reference.size || points.any { !it.isFinite() }) return null
        fun mean(p: List<Vector3>) = p.fold(Vector3(0f,0f,0f)) { a,b -> a+b }*(1f/p.size)
        val rc=referenceCenter; val pc=mean(points)
        val cross=DoubleArray(9)
        for(i in points.indices) {
            val a=reference[i]-rc; val b=points[i]-pc
            val av=doubleArrayOf(a.x.toDouble(),a.y.toDouble(),a.z.toDouble())
            val bv=doubleArrayOf(b.x.toDouble(),b.y.toDouble(),b.z.toDouble())
            for(r in 0..2) for(c in 0..2) {
                cross[r*3+c]+=bv[r]*av[c]
            }
        }
        val inverse=referenceInverse
        var rotation=DoubleArray(9) { i -> (0..2).sumOf { k -> cross[(i/3)*3+k]*inverse[k*3+i%3] } }
        var residual=0.0
        var spread=0.0
        for(i in points.indices) {
            val a=reference[i]-rc; val b=points[i]-pc
            val predicted=Vector3(
                (rotation[0]*a.x+rotation[1]*a.y+rotation[2]*a.z).toFloat(),
                (rotation[3]*a.x+rotation[4]*a.y+rotation[5]*a.z).toFloat(),
                (rotation[6]*a.x+rotation[7]*a.y+rotation[8]*a.z).toFloat())
            val error=predicted-b
            residual+=error.dot(error); spread+=b.dot(b)
        }
        // Missing/collapsed landmark sets must not manufacture an arbitrary rotation.
        if(spread<1.0 || residual>spread*.04) return null
        val norm=kotlin.math.sqrt(rotation.sumOf { it*it }/3.0)
        if(!norm.isFinite() || norm<.00001) return null
        rotation=rotation.map { it/norm }.toDoubleArray()
        // Polar decomposition removes fitted scale/shear and keeps a rigid orientation.
        repeat(6) {
            val inv=inverse(rotation) ?: return null
            rotation=DoubleArray(9) { i -> .5*(rotation[i]+inv[(i%3)*3+i/3]) }
        }
        fun column(i: Int)=Vector3(rotation[i].toFloat(),rotation[i+3].toFloat(),rotation[i+6].toFloat()).normalized()
        val x=column(0); val y=column(1); val z=column(2)
        if(!x.isFinite() || !y.isFinite() || !z.isFinite() || x.cross(y).dot(z)<.99f || z.z<=0f) return null
        return Axes(x,y,z)
    }

    fun rig(face: FaceAnalyzer.FaceMetrics, width: Int, height: Int, scale: Float, heightOffset: Float): GlassesRig? {
        if(face.landmarks.size<468) return null
        fun p(i: Int)=face.landmarks[i].let { Vector3(it.x*width,it.y*height,it.z*width) }
        val first=(p(33)+p(133))*.5f; val second=(p(263)+p(362))*.5f
        val fallback=GlassesRig.fromEyes(first,second,p(152),scale,heightOffset,p(10),p(168)) ?: return null
        val axes=fit(landmarkIndices.map { p(it) }) ?: return fallback
        val center=(first+second)*.5f
        val distance=(second-first).length()
        val depth=(p(168)-center).dot(axes.behind)-distance*.08f
        return GlassesRig(center+axes.behind*depth+axes.down*(distance*heightOffset),
            axes.right,axes.down,axes.behind,distance*scale)
    }

    private fun inverse(m: DoubleArray): DoubleArray? {
        val a=m[0];val b=m[1];val c=m[2];val d=m[3];val e=m[4];val f=m[5];val g=m[6];val h=m[7];val i=m[8]
        val det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g)
        if(!det.isFinite() || kotlin.math.abs(det)<1e-10) return null
        return doubleArrayOf(e*i-f*h,c*h-b*i,b*f-c*e,f*g-d*i,a*i-c*g,c*d-a*f,d*h-e*g,b*g-a*h,a*e-b*d).map { it/det }.toDoubleArray()
    }
}
