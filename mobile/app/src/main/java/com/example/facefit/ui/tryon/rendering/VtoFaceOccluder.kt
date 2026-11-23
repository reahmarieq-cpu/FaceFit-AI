package com.example.facefit.ui.tryon.rendering

import com.example.facefit.facefit.tryon.geometry.GlassesRig
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.geometry.VtoHeadOcclusionGeometry
import com.google.android.filament.*
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import android.opengl.Matrix as GlMatrix

/** Topology: google-ai-edge/mediapipe v0.10.14 canonical_face_model.obj (Apache-2.0).
 * Depth-only MediaPipe face surface. Its color writes are disabled, preserving camera pixels. */
internal class VtoFaceOccluder(
    private val engine: Engine,
    private val scene: Scene,
    private val loader: AssetLoader,
    resources: ResourceLoader,
    triangles: ByteArray
) {
    private val materialAsset = requireNotNull(loader.createAsset(materialGlb()))
    private val entity = EntityManager.get().create()
    private val topology: ByteArray = VtoHeadOcclusionGeometry.indices(triangles)
    private val vertices = VertexBuffer.Builder().vertexCount(VtoHeadOcclusionGeometry.vertexCount).bufferCount(1)
        .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 60)
        .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, 60)
        .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, 60)
        .attribute(VertexBuffer.VertexAttribute.UV1, 0, VertexBuffer.AttributeType.FLOAT2, 36, 60)
        .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT4, 44, 60)
        .build(engine)
    private val indices: IndexBuffer = IndexBuffer.Builder().indexCount(topology.size / 2)
        .bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
    private data class Job(val face: FaceAnalyzer.FaceMetrics, val width: Int, val height: Int,
        val rig: GlassesRig?, val inverse: FloatArray)
    private data class Prepared(val job: Job, val buffer: FloatBuffer?)
    private val worker = Executors.newSingleThreadExecutor()
    private val queued = AtomicReference<Job?>(null)
    private val prepared = AtomicReference<Prepared?>(null)
    private val scheduled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private var requested: Job? = null
    private var appliedInverse: FloatArray? = null
    private val displayTransform = FloatArray(16)
    private var shown = false
    private var previous: FaceAnalyzer.FaceMetrics? = null

    init {
        resources.loadResources(materialAsset)
        val manager = engine.renderableManager
        val source = materialAsset.entities.first { manager.hasComponent(it) }
        val material = manager.getMaterialInstanceAt(manager.getInstance(source), 0)
        material.setColorWrite(false)
        material.setDepthWrite(true)
        material.setCullingMode(Material.CullingMode.NONE)
        val data = ByteBuffer.allocateDirect(topology.size).order(ByteOrder.LITTLE_ENDIAN)
            .apply { put(topology); flip() }
        indices.setBuffer(engine, data)
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, -2000f, 10000f, 10000f, 10000f))
            .priority(0).culling(false).castShadows(false).receiveShadows(false)
            .material(0, material)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
            .build(engine, entity)
        val transforms = engine.transformManager
        if (transforms.getInstance(entity) == 0) transforms.create(entity)
        materialAsset.releaseSourceData()
    }

    private var previousWidth = 0
    private var previousHeight = 0
    private var validGeometry = false
    private var previousRig: GlassesRig? = null
    /** UI thread hands immutable CPU inputs off. Filament objects remain on the UI thread. */
    fun requestMesh(face: FaceAnalyzer.FaceMetrics, width: Int, height: Int, rig: GlassesRig?, inverse: FloatArray?) {
        if (inverse == null) {
            clearPreparedMesh()
            show(false)
            return
        }
        val job = Job(face, width, height, rig, inverse.copyOf())
        requested = job
        queued.set(job)
        schedulePreparation()
    }

    private fun schedulePreparation() {
        if (closed.get() || !scheduled.compareAndSet(false, true)) return
        worker.execute {
            try {
                while (!closed.get()) {
                    val job = queued.getAndSet(null) ?: break
                    val buffer = try { pack(job.face, job.width, job.height, job.rig) }
                        catch (error: Exception) {
                            android.util.Log.w("FilamentVTO", "Face mask preparation failed", error)
                            null
                        }
                    prepared.set(Prepared(job, buffer))
                }
            } finally {
                scheduled.set(false)
            }
        }
    }

    fun presentPreparedMesh(shownMatrix: FloatArray?) {
        val result = prepared.getAndSet(null)
        if (result != null && result.job === requested) {
            result.buffer?.let { vertices.setBufferAt(engine, 0, it) }
            appliedInverse = result.job.inverse.takeIf { result.buffer != null }
        }
        // Recover a publication that arrived just as the worker finished.
        if (queued.get() != null) schedulePreparation()
        val inverse = appliedInverse
        if (shownMatrix != null && inverse != null) {
            GlMatrix.multiplyMM(displayTransform, 0, shownMatrix, 0, inverse, 0)
            setTransform(displayTransform)
            show(true)
        } else show(false)
    }

    fun clearPreparedMesh() {
        requested = null
        queued.set(null)
        prepared.set(null)
        appliedInverse = null
    }

    private fun pack(face: FaceAnalyzer.FaceMetrics?, width: Int, height: Int, rig: GlassesRig?): FloatBuffer? {
        val positions = VtoHeadOcclusionGeometry.positions(face, width, height, rig) ?: return null
        val data = ByteBuffer.allocateDirect(VtoHeadOcclusionGeometry.vertexCount * 60)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (i in 0 until VtoHeadOcclusionGeometry.vertexCount) {
            data.put(positions, i * 3, 3)
            data.put(0f).put(0f).put(0f).put(1f)
            data.put(0f).put(0f).put(0f).put(0f)
            data.put(1f).put(1f).put(1f).put(1f)
        }
        data.flip()
        return data
    }

    fun update(face: FaceAnalyzer.FaceMetrics?, width: Int, height: Int, visible: Boolean, rig: GlassesRig? = null) {
        if (visible && (face !== previous || width != previousWidth || height != previousHeight || rig != previousRig)) {
            val data = pack(face, width, height, rig)
            validGeometry = data != null
            data?.let { vertices.setBufferAt(engine, 0, it) }
            previousRig = rig
            previous = face
            previousWidth = width
            previousHeight = height
        }
        show(visible && face != null && validGeometry)
    }

    private fun show(visible: Boolean) {
        if (visible == shown) return
        if (visible) scene.addEntity(entity) else scene.removeEntity(entity)
        shown = visible
    }

    fun setTransform(matrix: FloatArray) {
        val transforms = engine.transformManager
        transforms.setTransform(transforms.getInstance(entity), matrix)
    }

    fun close() {
        closed.set(true)
        clearPreparedMesh()
        worker.shutdown()
        scene.removeEntity(entity)
        engine.destroyEntity(entity)
        EntityManager.get().destroy(entity)
        engine.destroyVertexBuffer(vertices)
        engine.destroyIndexBuffer(indices)
        loader.destroyAsset(materialAsset)
    }

    companion object {
        // A position-only unlit material supplied by gltfio's existing shader provider.
        private fun materialGlb(): ByteBuffer {
            val json = """{"asset":{"version":"2.0"},"extensionsUsed":["KHR_materials_unlit"],"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],"meshes":[{"primitives":[{"attributes":{"POSITION":0},"material":0}]}],"materials":[{"extensions":{"KHR_materials_unlit":{}},"doubleSided":true}],"buffers":[{"byteLength":36}],"bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36}],"accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]}]}""".toByteArray()
            val length = (json.size + 3) and -4
            return ByteBuffer.allocateDirect(12 + 8 + length + 8 + 36).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0x46546c67); putInt(2); putInt(capacity())
                putInt(length); putInt(0x4e4f534a); put(json)
                repeat(length - json.size) { put(32.toByte()) }
                putInt(36); putInt(0x004e4942)
                for (v in floatArrayOf(0f,0f,0f, 1f,0f,0f, 0f,1f,0f)) putFloat(v)
                flip()
            }
        }
    }
}