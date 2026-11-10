package com.example.facefit.ui.tryon.rendering

import com.example.facefit.ui.tryon.tracking.VtoDiagnostics
import com.example.facefit.ui.tryon.tracking.VtoTrackingFeed

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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.facefit.facefit.tryon.tracking.VtoHeadPose
import com.example.facefit.facefit.tryon.geometry.GlbFaceTransform
import com.example.facefit.facefit.tryon.geometry.VtoAutomaticFit
import com.google.android.filament.*
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

@Composable
fun FilamentGlassesOverlay(assetPath: String, state: Glasses3DState,
    onReady: () -> Unit, onError: (String) -> Unit, trackingFeed: VtoTrackingFeed? = null) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val readyCallback by rememberUpdatedState(onReady)
    val errorCallback by rememberUpdatedState(onError)
    var bytes by remember(assetPath) { mutableStateOf<ByteArray?>(null) }
    var triangles by remember { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(assetPath) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                context.assets.open(assetPath).use { it.readBytes() } to
                    context.assets.open("vto/face_triangles.bin").use { it.readBytes() }
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
                TrackedGlassesView(ctx, owner, modelBytes, requireNotNull(triangles), VtoAutomaticFit.forAsset(assetPath), { readyCallback() }, { errorCallback(it) })
            }, modifier = Modifier.fillMaxSize(), update = { it.trackingState = state; it.trackingFeed = trackingFeed })
        }
    }
}

internal class TrackedGlassesView(context: Context, private val owner: LifecycleOwner,
    private val bytes: ByteArray, private val triangles: ByteArray,
    private val calibration: VtoAutomaticFit.ModelAnchors?, private val onReady: () -> Unit,
    private val onError: (String) -> Unit) : TextureView(context), Choreographer.FrameCallback {
    var trackingState = Glasses3DState(null)
    var trackingFeed: VtoTrackingFeed? = null
    private var session: TrackedGlassesSession? = null
    private var running = false
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
        if (running) choreographer.postFrameCallback(this) else choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        try {
            val face=if(trackingFeed!=null) trackingFeed?.latest else trackingState.metrics
            session?.render(frameTimeNanos, trackingState.copy(metrics=face,
                scale = 1f, heightOffset = 0f))
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
    private var swapChain: SwapChain? = null
    private var width = 0
    private var height = 0
    private var visible = false
    private var occluder: VtoFaceOccluder? = null
    private var poseFace: com.example.facefit.facefit.analysis.FaceAnalyzer.FaceMetrics? = null
    private var poseWidth=0
    private var poseHeight=0
    private var poseScale=Float.NaN
    private var poseOffset=Float.NaN
    private var poseMatrix: FloatArray? = null
    private var pendingFrame=true

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

    fun render(time: Long, state: Glasses3DState) {
        if (!helper.isReadyToRender || width <= 0 || height <= 0) return
        val model = asset ?: return
        val diagnosticStart=android.os.SystemClock.uptimeMillis()
        val changed=state.metrics !== poseFace || width!=poseWidth || height!=poseHeight ||
            state.scale!=poseScale || state.heightOffset!=poseOffset
        if(changed) {
            pendingFrame=true
            val rig=state.metrics?.takeIf { it.isFaceDetected }?.let {
                VtoHeadPose.rig(it,width,height,state.scale,state.heightOffset)
            }
            poseMatrix=GlbFaceTransform.matrix(state.metrics,width,height,
                model.boundingBox.center,model.boundingBox.halfExtent,state.scale,state.heightOffset,calibration,rig)
            poseMatrix?.let { engine.transformManager.setTransform(engine.transformManager.getInstance(model.root),it) }
            occluder?.update(state.metrics,width,height,poseMatrix!=null,rig)
            poseFace=state.metrics;poseWidth=width;poseHeight=height
            poseScale=state.scale;poseOffset=state.heightOffset
        }
        val diagnosticPoseDone=android.os.SystemClock.uptimeMillis()
        if(!pendingFrame) return
        val show=poseMatrix!=null
        if (show != visible) {
            if (show) scene.addEntities(model.entities) else scene.removeEntities(model.entities)
            visible = show
        }
        // Clear transparent pixels even after tracking is lost, so the last pose cannot linger.
        val target = swapChain ?: return
        val submitted=renderer.beginFrame(target,time)
        if (submitted) {
            renderer.render(view)
            renderer.endFrame()
            pendingFrame=false
        }
        VtoDiagnostics.render(state.metrics,diagnosticStart,diagnosticPoseDone,
            android.os.SystemClock.uptimeMillis(),submitted,show)
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
