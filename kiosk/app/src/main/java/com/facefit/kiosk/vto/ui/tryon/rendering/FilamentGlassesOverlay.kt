package com.facefit.kiosk.vto.ui.tryon.rendering

import com.facefit.kiosk.vto.ui.tryon.tracking.VtoDiagnostics
import com.facefit.kiosk.vto.ui.tryon.tracking.VtoTrackingFeed

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.facefit.kiosk.vto.facefit.tryon.tracking.VtoHeadPose
import com.facefit.kiosk.vto.facefit.tryon.tracking.VtoPoseSmoother
import com.facefit.kiosk.vto.facefit.tryon.geometry.GlassesRig
import com.facefit.kiosk.vto.facefit.tryon.geometry.GlbFaceTransform
import com.facefit.kiosk.vto.facefit.tryon.geometry.VtoAutomaticFit
import com.facefit.kiosk.vto.facefit.tryon.geometry.Vector3
import com.google.android.filament.*
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import com.google.android.filament.utils.Utils
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer

@Composable
fun FilamentGlassesOverlay(assetPath: String, state: Glasses3DState,
    onReady: () -> Unit, onError: (String) -> Unit, trackingFeed: VtoTrackingFeed? = null,
    fromStorage: Boolean = false, onFaceVisible: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val readyCallback by rememberUpdatedState(onReady)
    val visibilityCallback by rememberUpdatedState(onFaceVisible)
    val errorCallback by rememberUpdatedState(onError)
    var bytes by remember(assetPath, fromStorage) { mutableStateOf<ByteArray?>(null) }
    var triangles by remember { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(assetPath, fromStorage) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                val model = if (fromStorage) {
                    withTimeoutOrNull(20_000L) {
                        FirebaseStorage.getInstance().reference.child(assetPath)
                            .getBytes(24L * 1024 * 1024).await()
                    } ?: error("Glasses model download timed out")
                } else {
                    context.assets.open(assetPath).use { it.readBytes() }
                }
                require(model.size >= 12 && ByteBuffer.wrap(model).int == 0x676c5446) { "Invalid GLB" }
                model to context.assets.open("vto/face_triangles.bin").use { it.readBytes() }
            }
            triangles = loaded.second
            bytes = loaded.first
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            android.util.Log.e("FilamentVTO", "Cannot read $assetPath", error)
            errorCallback("The selected glasses model could not be opened.")
        }
    }
    bytes?.let { modelBytes ->
        key(assetPath, owner) {
            AndroidView(factory = { ctx ->
                TrackedGlassesView(ctx, owner, modelBytes, requireNotNull(triangles), VtoAutomaticFit.forAsset(assetPath), { readyCallback() }, { errorCallback(it) }, { visibilityCallback(it) })
            }, modifier = Modifier.fillMaxSize(), update = { it.trackingState = state; it.trackingFeed = trackingFeed })
        }
    }
}

internal class TrackedGlassesView(context: Context, private val owner: LifecycleOwner,
    private val bytes: ByteArray, private val triangles: ByteArray,
    private val calibration: VtoAutomaticFit.ModelAnchors?, private val onReady: () -> Unit,
    private val onError: (String) -> Unit, private val onFaceVisible: (Boolean) -> Unit) : TextureView(context), Choreographer.FrameCallback {
    var trackingState = Glasses3DState(null)
    var trackingFeed: VtoTrackingFeed? = null
    private var session: TrackedGlassesSession? = null
    private var running = false
    private var faceVisible = false
    private val choreographer = Choreographer.getInstance()
    private val observer = LifecycleEventObserver { _, _ -> updateRunning() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isOpaque = false
        try {
            Utils.init()
            val created = TrackedGlassesSession(calibration)
            session = created
            created.load(this, bytes, triangles)
            owner.lifecycle.addObserver(observer)
            updateRunning()
            post { if (session === created) onReady() }
        } catch (error: Exception) {
            release()
            report(error)
        }
    }

    private fun updateRunning() {
        val next = session != null && isAttachedToWindow &&
            owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (running == next) return
        running = next
        if (running) choreographer.postFrameCallback(this) else {
            choreographer.removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        try {
            val feed = trackingFeed
            val source = if (feed != null) feed.freshAt(frameTimeNanos) else trackingState.metrics
            val visible = session?.render(frameTimeNanos, trackingState.copy(metrics = source,
                scale = 1f, heightOffset = 0f), diagnosticFace = feed?.latest ?: source) == true
            if (faceVisible != visible) {
                faceVisible = visible
                onFaceVisible(visible)
            }
            choreographer.postFrameCallback(this)
        } catch (error: Exception) {
            release()
            report(error)
        }
    }

    private fun report(error: Exception) {
        android.util.Log.e("FilamentVTO", "3D try-on failed", error)
        post { onError("3D try-on could not start. Go back and reopen try-on.") }
    }

    private fun release() {
        running = false
        choreographer.removeFrameCallback(this)
        owner.lifecycle.removeObserver(observer)
        session?.close()
        session = null
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}

/** Resolve model coordinates before the renderer applies the camera-space root transform. */
internal fun resolveGlbFitAnchors(
    asset: FilamentAsset,
    transforms: TransformManager,
    fallback: VtoAutomaticFit.ModelAnchors? = null
): VtoAutomaticFit.ModelAnchors? {
    fun position(name: String): Vector3? {
        val entity = asset.getFirstEntityByName(name)
        if (entity == 0) return null
        val instance = transforms.getInstance(entity)
        if (instance == 0) return null
        val matrix = transforms.getWorldTransform(instance, FloatArray(16))
        return Vector3(matrix[12], matrix[13], matrix[14]).takeIf { it.isFinite() }
    }

    val leftLens = position("anchor_lens_image_left")
    val rightLens = position("anchor_lens_image_right")
    val optical = if (leftLens != null && rightLens != null) {
        VtoAutomaticFit.ModelAnchors(leftLens, rightLens).takeIf { it.isValid() } ?: fallback
    } else fallback
    val bridge = position("anchor_bridge")
    return optical?.copy(bridge = bridge ?: optical.bridge)?.withHinges(
        position("anchor_hinge_image_left"), position("anchor_hinge_image_right")
    )
}

/** All native objects and calls belong to the UI thread; no orbit camera overwrites the face pose. */
internal class TrackedGlassesSession(private val calibration: VtoAutomaticFit.ModelAnchors? = null) {
    private val engine = Engine.create()
    private val renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val entities = EntityManager.get()
    private val cameraEntity = entities.create()
    private val camera = engine.createCamera(cameraEntity)
    private val materials = UbershaderProvider(engine)
    private val loader = AssetLoader(engine, materials, entities)
    private val resources = ResourceLoader(engine)
    private val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val light = entities.create()
    private var ambient: IndirectLight? = null
    private var asset: FilamentAsset? = null
    private var fitAnchors: VtoAutomaticFit.ModelAnchors? = calibration
    private lateinit var modelCenter: FloatArray
    private lateinit var modelHalfExtent: FloatArray
    private var modelTransform = 0
    private var swapChain: SwapChain? = null
    private var width = 0
    private var height = 0
    private var visible = false
    private var occluder: VtoFaceOccluder? = null
    private var poseFace: com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer.FaceMetrics? = null
    private var poseWidth=0
    private var poseHeight=0
    private var poseScale=Float.NaN
    private var poseOffset=Float.NaN
    private val smoother = VtoPoseSmoother()
    private var maskFace: FaceAnalyzer.FaceMetrics? = null
    private var measuredRig: GlassesRig? = null

    fun load(texture: TextureView, bytes: ByteArray, triangles: ByteArray) {
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        renderer.clearOptions = Renderer.ClearOptions().apply { clear = true }
        camera.setExposure(16f, 1f / 125f, 100f)
        // The identity camera looks down -Z; the orthographic projection matches CameraX pixels.
        camera.lookAt(0.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0)
        ambient = IndirectLight.Builder().irradiance(1, floatArrayOf(0.8f, 0.8f, 0.8f))
            .intensity(30_000f).build(engine)
        scene.indirectLight = ambient
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(1f, 1f, 1f)
            .intensity(100_000f).direction(-0.5f, -0.8f, -1f).castShadows(false).build(engine, light)
        scene.addEntity(light)
        val buffer = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); flip() }
        val model = requireNotNull(loader.createAsset(buffer)) { "Invalid GLB" }
        asset = model
        resources.loadResources(model)
        fitAnchors = resolveGlbFitAnchors(model, engine.transformManager, calibration)
        modelCenter = model.boundingBox.center
        modelHalfExtent = model.boundingBox.halfExtent
        modelTransform = engine.transformManager.getInstance(model.root)
        model.releaseSourceData()
        occluder = try { VtoFaceOccluder(engine, scene, loader, resources, triangles) }
        catch (failure: Exception) {
            android.util.Log.w("FilamentVTO", "Face hiding unavailable; keeping glasses visible", failure)
            null
        }
        helper.isOpaque = false
        helper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, helper.swapChainFlags)
            }
            override fun onDetachedFromSurface() {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = null
                engine.flushAndWait()
            }
            override fun onResized(w: Int, h: Int) {
                width = w
                height = h
                view.viewport = Viewport(0, 0, w, h)
                if (w > 0 && h > 0) camera.setProjection(Camera.Projection.ORTHO,
                    0.0, w.toDouble(), 0.0, h.toDouble(), 1.0, 10000.0)
                engine.flushAndWait()
            }
        }
        helper.attachTo(texture)
    }

    fun render(time: Long, state: Glasses3DState, diagnosticFace: FaceAnalyzer.FaceMetrics? = state.metrics): Boolean {
        if (!helper.isReadyToRender || width <= 0 || height <= 0) return false
        val model = asset ?: return false
        val diagnosticStart=android.os.SystemClock.uptimeMillis()
        val changed=state.metrics !== poseFace || width!=poseWidth || height!=poseHeight ||
            state.scale!=poseScale || state.heightOffset!=poseOffset
        if (changed) {
            if (width != poseWidth || height != poseHeight || state.scale != poseScale || state.heightOffset != poseOffset) {
                smoother.update(null)
            }
            val face = state.metrics
            measuredRig = face?.takeIf { it.isFaceDetected }?.let {
                VtoHeadPose.rig(it, width, height, state.scale, state.heightOffset)
            }
            smoother.update(measuredRig)
            maskFace = face?.let { it.vtoMaskLandmarks?.let { points -> it.copy(landmarks = points) } ?: it }
            poseFace = face
            poseWidth = width
            poseHeight = height
            poseScale = state.scale
            poseOffset = state.heightOffset
        }
        val rig = smoother.latest()
        val matrix = rig?.let {
            GlbFaceTransform.matrix(state.metrics, width, height, modelCenter,
                modelHalfExtent, state.scale, state.heightOffset, fitAnchors, it)
        }
        if (matrix != null) {
            engine.transformManager.setTransform(modelTransform, matrix)
        }
        val show = matrix != null
        occluder?.update(maskFace, width, height, show, rig)
        val diagnosticPoseDone = android.os.SystemClock.uptimeMillis()
        if (show != visible) {
            if (show) scene.addEntities(model.entities) else scene.removeEntities(model.entities)
            visible = show
        }
        // Clear transparent pixels even after tracking is lost, so the last pose cannot linger.
        val target = swapChain ?: return false
        val submitted=renderer.beginFrame(target,time)
        if (submitted) {
            renderer.render(view)
            renderer.endFrame()

        }
        VtoDiagnostics.render(diagnosticFace,diagnosticStart,diagnosticPoseDone,
            android.os.SystemClock.uptimeMillis(),submitted,show)
        return show
    }

    fun close() {
        helper.detach()
        occluder?.close()
        occluder = null
        asset?.let { scene.removeEntities(it.entities); loader.destroyAsset(it) }
        asset = null
        resources.destroy()
        loader.destroy()
        materials.destroyMaterials()
        materials.destroy()
        scene.indirectLight = null
        ambient?.let { engine.destroyIndirectLight(it) }
        engine.destroyEntity(light)
        entities.destroy(light)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        entities.destroy(cameraEntity)
        engine.destroyRenderer(renderer)
        engine.destroy()
    }
}
