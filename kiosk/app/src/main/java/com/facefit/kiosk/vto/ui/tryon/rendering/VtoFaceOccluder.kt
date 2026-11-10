package com.facefit.kiosk.vto.ui.tryon.rendering

import com.facefit.kiosk.vto.facefit.tryon.geometry.GlassesRig
import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.tryon.geometry.VtoHeadOcclusionGeometry
import com.google.android.filament.*
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
        materialAsset.releaseSourceData()
    }

    private var previousWidth = 0
    private var previousHeight = 0
    private var validGeometry = false
    fun update(face: FaceAnalyzer.FaceMetrics?, width: Int, height: Int, visible: Boolean, rig: GlassesRig? = null) {
        if (visible && (face !== previous || width != previousWidth || height != previousHeight)) {
            val positions = VtoHeadOcclusionGeometry.positions(face, width, height, rig)
            validGeometry = positions != null
            if (positions != null) {
                val data = ByteBuffer.allocateDirect(VtoHeadOcclusionGeometry.vertexCount * 60)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
                for (i in 0 until VtoHeadOcclusionGeometry.vertexCount) {
                    data.put(positions, i * 3, 3)
                    data.put(0f).put(0f).put(0f).put(1f)
                    data.put(0f).put(0f).put(0f).put(0f)
                    data.put(1f).put(1f).put(1f).put(1f)
                }
                data.flip()
                vertices.setBufferAt(engine, 0, data)
            }
            previous = face
            previousWidth = width
            previousHeight = height
        }
        val show = visible && face != null && validGeometry
        if (show != shown) {
            if (show) scene.addEntity(entity) else scene.removeEntity(entity)
            shown = show
        }
    }

    fun close() {
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