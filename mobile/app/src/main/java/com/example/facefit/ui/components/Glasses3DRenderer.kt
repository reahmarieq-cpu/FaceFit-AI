package com.example.facefit.ui.components

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.example.facefit.logic.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

data class Glasses3DState(
    val metrics: FaceAnalyzer.FaceMetrics?,
    val scale: Float = 1f,
    val heightOffset: Float = 0f,
    val clipTop: Int = 0,
    val clipBottom: Int = 0
)

class Glasses3DRenderer(browline: Boolean, private val onError: (String) -> Unit) : GLSurfaceView.Renderer {
    @Volatile var state = Glasses3DState(null)
    private val model = GlassesMesh.parts(browline)
    private val frontBuffer = buffer(model.front)
    private val templeBuffer = buffer(model.temples)
    private val lensBuffer = buffer(model.lenses)
    private var width = 1
    private var height = 1
    private var program = 0
    private var position = 0
    private var normal = 0
    private var color = 0
    private var modelUniform = 0
    private var viewUniform = 0
    private var surfaceUniform = 0
    private var failed = false
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
    private val faceTriangles = FaceMeshTopology.triangles
    private var faceBuffer: FloatBuffer? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            program = GLES20.glCreateProgram()
            val vertex = shader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val fragment = shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val success = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, success, 0)
            check(success[0] != 0) { GLES20.glGetProgramInfoLog(program) }
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            position = GLES20.glGetAttribLocation(program, "aPosition")
            normal = GLES20.glGetAttribLocation(program, "aNormal")
            color = GLES20.glGetAttribLocation(program, "aColor")
            modelUniform = GLES20.glGetUniformLocation(program, "uModel")
            viewUniform = GLES20.glGetUniformLocation(program, "uViewSize")
            surfaceUniform = GLES20.glGetUniformLocation(program, "uLens")
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            failed = false
        } catch (error: Exception) { fail(error) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glColorMask(true, true, true, true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (failed) return
        val current = state
        val face = current.metrics ?: return
        if (!face.isFaceDetected || face.landmarks.size < 468) return
        try {
            fun point(index: Int): Vector3 {
                val p = face.landmarks[index]
                return Vector3(p.x * width, p.y * height, p.z * width)
            }
            val rig = GlassesRig.fromEyes((point(33) + point(133)) * 0.5f,
                (point(263) + point(362)) * 0.5f, point(152), current.scale, current.heightOffset,
                forehead = point(10), noseBridge = point(168)) ?: return
            if (face.landmarks.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) return
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            GLES20.glScissor(0, current.clipBottom.coerceIn(0, height), width,
                (height - current.clipTop - current.clipBottom).coerceAtLeast(0))
            GLES20.glUseProgram(program)
            GLES20.glUniform1f(surfaceUniform, 0f)
            GLES20.glUniform2f(viewUniform, width.toFloat(), height.toFloat())
            GLES20.glEnableVertexAttribArray(position)

            // Render the tracked face into depth only. It hides arms that pass behind the skin.
            val depth = faceBuffer ?: ByteBuffer.allocateDirect(faceTriangles.size * 12)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().also { faceBuffer = it }
            depth.clear()
            for (index in faceTriangles) {
                val p = face.landmarks[index]
                depth.put(p.x * width).put(p.y * height).put(p.z * width)
            }
            depth.position(0)
            GLES20.glUniformMatrix4fv(modelUniform, 1, false, identity, 0)
            GLES20.glDisableVertexAttribArray(normal)
            GLES20.glDisableVertexAttribArray(color)
            GLES20.glVertexAttrib3f(normal, 0f, 0f, -1f)
            GLES20.glVertexAttrib3f(color, 0f, 0f, 0f)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, depth)
            GLES20.glColorMask(false, false, false, false)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, faceTriangles.size)
            GLES20.glColorMask(true, true, true, true)

            GLES20.glUniformMatrix4fv(modelUniform, 1, false, rig.matrix(), 0)
            GLES20.glEnableVertexAttribArray(normal)
            GLES20.glEnableVertexAttribArray(color)
            fun draw(vertices: FloatArray, vertexBuffer: FloatBuffer) {
                vertexBuffer.position(0)
                GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 36, vertexBuffer)
                vertexBuffer.position(3)
                GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 36, vertexBuffer)
                vertexBuffer.position(6)
                GLES20.glVertexAttribPointer(color, 3, GLES20.GL_FLOAT, false, 36, vertexBuffer)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices.size / 9)
            }
            draw(model.temples, templeBuffer)
            // Skin depth should occlude the arms, not punch holes through the lens rims.
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
            draw(model.front, frontBuffer)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,
                GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glDepthMask(false)
            GLES20.glUniform1f(surfaceUniform, 1f)
            draw(model.lenses, lensBuffer)
            GLES20.glDepthMask(true)
            GLES20.glDisable(GLES20.GL_BLEND)
            check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "OpenGL draw failed" }
        } catch (error: Exception) { fail(error) }
    }

    private fun fail(error: Exception) {
        android.util.Log.e("Glasses3D", "3D rendering failed", error)
        failed = true
        onError("3D try-on could not start on this device.")
    }

    private fun shader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val success = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, success, 0)
        check(success[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    companion object {
        private fun buffer(values: FloatArray) = ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
        private const val VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            uniform mat4 uModel;
            uniform vec2 uViewSize;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying vec2 vLocal;
            void main() {
                vec3 p = (uModel * vec4(aPosition, 1.0)).xyz;
                gl_Position = vec4(2.0 * p.x / uViewSize.x - 1.0,
                    1.0 - 2.0 * p.y / uViewSize.y, p.z / (uViewSize.x * 4.0), 1.0);
                vNormal = mat3(uModel) * aNormal;
                vColor = aColor;
                vLocal = aPosition.xy;
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying vec2 vLocal;
            uniform float uLens;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 view = vec3(0.0, 0.0, -1.0);
                vec3 light = normalize(vec3(-0.4, -0.7, -1.0));
                float diffuse = max(dot(n, light), 0.0);
                float facing = abs(dot(n, view));
                float fresnel = pow(1.0 - facing, 5.0);
                vec3 reflected = reflect(-view, n);
                float window = pow(max(dot(reflected, normalize(vec3(-0.45, -0.65, -1.0))), 0.0), 18.0);
                float fill = pow(max(dot(reflected, normalize(vec3(0.7, -0.2, -1.0))), 0.0), 10.0);
                if (uLens > 0.5) {
                    float band = vLocal.y + 0.13 + 0.12 * vLocal.x;
                    float reflection = exp(-220.0 * band * band) * window;
                    gl_FragColor = vec4(vec3(0.78, 0.88, 0.95),
                        0.018 + 0.05 * fresnel + 0.055 * reflection);
                } else {
                    float metallic = step(0.12, (vColor.r + vColor.g + vColor.b) / 3.0);
                    float specular = window * mix(0.12, 0.38, metallic) + fill * mix(0.035, 0.15, metallic);
                    vec3 base = vColor * (0.65 + 0.35 * diffuse);
                    gl_FragColor = vec4(base + vec3(specular + fresnel * 0.045), 1.0);
                }
            }
        """
    }
}
