package com.example.facefit.ui.tryon.rendering

import com.example.facefit.ui.tryon.tracking.VtoDiagnostics
import com.example.facefit.ui.camera.VtoThermalMonitor
import com.example.facefit.ui.tryon.tracking.VtoMotionFeed
import com.example.facefit.ui.tryon.tracking.VtoTrackingFeed

import android.content.Context
import android.opengl.Matrix as GlMatrix
import com.example.facefit.facefit.analysis.FaceAnalyzer.FaceMetrics
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
import android.os.SystemClock
import com.example.facefit.facefit.tryon.geometry.TryOnGeometry
import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.facefit.tryon.tracking.VtoDisplayFollower
import com.example.facefit.facefit.tryon.tracking.VtoHeadPose
import com.example.facefit.facefit.tryon.tracking.VtoRigResolver
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
    onReady: () -> Unit, onError: (String) -> Unit, trackingFeed: VtoTrackingFeed? = null,
    motionFeed: VtoMotionFeed? = null) {
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
            fun glassesView(ctx: Context) = TrackedGlassesView(ctx, owner, modelBytes,
                requireNotNull(triangles), VtoAutomaticFit.forAsset(assetPath),
                { readyCallback() }, { errorCallback(it) })
            AndroidView(factory = { glassesView(it) }, modifier = Modifier.fillMaxSize(), update = {
                it.trackingState = state
                it.trackingFeed = trackingFeed
                it.motionFeed = motionFeed
            })
        }
    }
}

internal class TrackedGlassesView(context: Context, private val owner: LifecycleOwner,
    private val bytes: ByteArray, private val triangles: ByteArray,
    private val calibration: VtoAutomaticFit.ModelAnchors?, private val onReady: () -> Unit,
    private val onError: (String) -> Unit) : TextureView(context), Choreographer.FrameCallback {
    var trackingState = Glasses3DState(null)
    var trackingFeed: VtoTrackingFeed? = null
    var motionFeed: VtoMotionFeed? = null
    private var session: TrackedGlassesSession? = null
    private var running = false
    private val follower = VtoDisplayFollower()
    private val thermal = VtoThermalMonitor.create(context)
    private val choreographer = Choreographer.getInstance()
    private val observer = LifecycleEventObserver { _, _ -> updateRunning() }

    init { isOpaque = false }

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
        if (running) {
            thermal.start()
            choreographer.postFrameCallback(this)
        } else {
            thermal.close()
            choreographer.removeFrameCallback(this)
            follower.reset()
            session?.resetPoseStabilizer()
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        try {
            val trackingNow = SystemClock.elapsedRealtimeNanos()
            val budget = thermal.budget
            session?.setSecondaryPasses(budget.secondaryPasses)
            val cameraMotion = motionFeed
            val feed = trackingFeed
            val holdingAnchor = cameraMotion?.holdingAnchor == true
            val freshMesh = feed?.latestAt(frameTimeNanos)
            val frame = cameraMotion?.latest?.takeIf {
                    it.face.imageWidth == width && it.face.imageHeight == height
            }
            val face = if (cameraMotion != null) frame?.face?.takeIf { feed == null || it === freshMesh || holdingAnchor }
                else if (feed != null) freshMesh else trackingState.metrics
            val displayed = if (frame != null && face?.isFaceDetected == true) {
                VtoDisplayFollower.Display(face, 1f)
            } else {
                follower.sample(face, trackingNow)
            }
            if (alpha < 1f && displayed.opacity == 1f) session?.resetPoseStabilizer()
            if (alpha != displayed.opacity) alpha=displayed.opacity
            if (frame != null && face?.isFaceDetected == true && displayed.face != null) {
                renderTrackedPose(frame, frameTimeNanos, trackingNow)
            } else {
                clearShownMesh()
                session?.render(frameTimeNanos, trackingState.copy(metrics = displayed.face,
                    scale = 1f, heightOffset = 0f), face,
                    trackingNow = trackingNow, forceSubmit = displayed.face != null)
            }
        } catch (error: Exception) {
            release()
            report(error)
        } finally {
            if (running) choreographer.postFrameCallback(this)
        }
    }

    private var shiftsApplied = 0
    private var shiftAgeNanos = 0L
    private var shiftFramesRendered = 0
    private var shiftLogAt = 0L

    private var shownFrame: VtoMotionFeed.Frame? = null
    private var shownShift: VtoMotionFeed.ShiftSample? = null

    internal fun clearShownMesh() { shownFrame = null; shownShift = null }

    private fun renderTrackedPose(latest: VtoMotionFeed.Frame, frameNanos: Long,
        trackingNow: Long) {
        // Only a shift measured against this exact mesh frame may move it.
        val sample = motionFeed?.shift
        val matching = sample?.takeIf { latest.referenceId != 0L && it.shift.referenceId == latest.referenceId }
        val previous = shownFrame
        val frame: VtoMotionFeed.Frame
        val shift: VtoMotionFeed.ShiftSample?
        when (VtoMeshChoice.pick(latestIsShown = previous === latest, latestHasMatch = matching != null,
            latestTracked = latest.referenceId != 0L, hasShown = previous != null,
            waitedNanos = System.nanoTime() - latest.deliveredAtNanos)) {
            VtoMeshChoice.Pick.LATEST_WITH_NEW_SHIFT -> {
                frame = latest; shift = matching
                shownFrame = latest; shownShift = matching
            }
            // The next mesh's first match is published just before that mesh reaches the screen.
            // Dropping the correction for that frame flicked the glasses ~300 ms back.
            VtoMeshChoice.Pick.LATEST_WITH_LAST_SHIFT -> { frame = latest; shift = shownShift }
            // A new mesh without its first match yet: keep the tracked previous mesh briefly.
            VtoMeshChoice.Pick.SHOWN_WITH_LAST_SHIFT -> { frame = previous!!; shift = shownShift }
            VtoMeshChoice.Pick.LATEST_UNTRACKED -> {
                frame = latest; shift = null
                shownFrame = latest; shownShift = null
            }
        }
        session?.render(frameNanos,
            trackingState.copy(metrics = frame.face, scale = 1f, heightOffset = 0f), frame.face,
            trackingNow = trackingNow, forceSubmit = true, trackedFrame = frame,
            frameShift = shift?.shift,
            sampleNanos = shift?.capturedElapsedNanos,
            )
        if (com.example.facefit.BuildConfig.DEBUG && android.util.Log.isLoggable("FaceFitVTOTiming", android.util.Log.VERBOSE)) {
            shiftFramesRendered++
            if (shift != null) { shiftsApplied++; shiftAgeNanos += trackingNow - shift.capturedElapsedNanos }
            if (shiftLogAt == 0L) shiftLogAt = trackingNow
            if (trackingNow - shiftLogAt >= 1_000_000_000L) {
                android.util.Log.i("FaceFitVTODiag", "event=shiftRender renderedFrames=$shiftFramesRendered " +
                    "shiftedPercent=${shiftsApplied * 100 / shiftFramesRendered} " +
                    "meanShiftAgeMs=${if (shiftsApplied > 0) shiftAgeNanos / 1e6 / shiftsApplied else Double.NaN} " +
                    "meshAgeMs=${frame.measuredLatencyNanos / 1e6}")
                shiftFramesRendered = 0; shiftsApplied = 0; shiftAgeNanos = 0L; shiftLogAt = trackingNow
            }
        }
    }

    private fun report(error: Exception) {
        android.util.Log.e("FilamentVTO", "3D try-on failed", error)
        post { onError("3D try-on could not start. Go back and reopen try-on.") }
    }

    private fun release() {
        clearShownMesh()
        running = false
        thermal.close()
        follower.reset()
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
    private var cachedMesh: VtoMotionFeed.Frame? = null
    private var cachedMeshMatrix: FloatArray? = null
    private var cachedMeshRig: com.example.facefit.facefit.tryon.geometry.GlassesRig? = null
    private var cachedMeshWidth = 0
    private var cachedMeshHeight = 0
    private val inverseMeshMatrix = FloatArray(16)
    private var hasMeshInverse = false
    private val maskTransform = FloatArray(16)
    private var poseFace: com.example.facefit.facefit.analysis.FaceAnalyzer.FaceMetrics? = null
    private var poseWidth=0
    private var poseHeight=0
    private var poseScale=Float.NaN
    private var poseOffset=Float.NaN
    private var modelAnchor: com.example.facefit.facefit.tryon.geometry.Vector3? = null
    private var rawPoseMatrix: FloatArray? = null
    private var poseMatrix: FloatArray? = null
    private val displayPose = VtoDisplayPose()
    private val eyePoints = FloatArray(8)
    private val shiftedMatrix = FloatArray(16)
    private val meshCorrection = VtoMeshCorrection()
    private var loggedPlacement: VtoMotionFeed.Frame? = null
    private val motionLead = VtoMotionLead()
    private val lead = FloatArray(2)
    private val correction = FloatArray(2)
    private val correctedMatrix = FloatArray(16)

    fun resetPoseStabilizer() {
        displayPose.reset()
    }
    private var pendingFrame=true
    private var secondaryPasses: Boolean? = null
    private var poseRig: com.example.facefit.facefit.tryon.geometry.GlassesRig? = null
    private var lastMeasurement: com.example.facefit.facefit.analysis.FaceAnalyzer.FaceMetrics? = null
    private val rigResolver=VtoRigResolver()
    private val gpuTiming=VtoGpuTiming(engine)

    fun setSecondaryPasses(enabled: Boolean) {
        if (secondaryPasses == enabled) return
        secondaryPasses = enabled
        view.setPostProcessingEnabled(enabled)
        view.setAntiAliasing(if (enabled) View.AntiAliasing.FXAA else View.AntiAliasing.NONE)
        view.setShadowingEnabled(enabled)
        engine.lightManager.setShadowCaster(engine.lightManager.getInstance(light), enabled)
        pendingFrame = true
    }

    fun load(texture: TextureView, bytes: ByteArray, triangles: ByteArray) {
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
        camera.setExposure(16f, 1f / 125f, 100f)
        // The identity camera looks down -Z; the orthographic projection matches CameraX pixels.
        camera.lookAt(0.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0)
        ambient = IndirectLight.Builder().irradiance(1, floatArrayOf(0.8f, 0.8f, 0.8f))
            .intensity(30_000f).build(engine)
        scene.indirectLight = ambient
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(1f, 1f, 1f)
            .intensity(100_000f).direction(-0.5f, -0.8f, -1f).castShadows(false).build(engine, light)
        scene.addEntity(light)
        setSecondaryPasses(true)
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
                pendingFrame = true
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

    fun render(time: Long, state: Glasses3DState,
        diagnosticFace: com.example.facefit.facefit.analysis.FaceAnalyzer.FaceMetrics? = state.metrics,
        trackingNow: Long = SystemClock.elapsedRealtimeNanos(),
        forceSubmit: Boolean = false,
        trackedFrame: VtoMotionFeed.Frame? = null,
        frameShift: com.example.facefit.facefit.tryon.tracking.VtoFrameShiftTracker.Shift? = null,
        sampleNanos: Long? = null): Boolean {
        if (com.example.facefit.BuildConfig.DEBUG &&
            android.util.Log.isLoggable("FaceFitBenchHide", android.util.Log.VERBOSE)) {
            return false
        }
        gpuTiming.poll()
        if (!helper.isReadyToRender || width <= 0 || height <= 0) return false
        val model = asset ?: return false
        val diagnosticStart=android.os.SystemClock.uptimeMillis()
        // Follower runs at display rate, with stable identity once converged.
        // Model and mask share the followed landmarks and fit used to build the target pose.
        val cachedChanged = trackedFrame != null &&
            (trackedFrame !== cachedMesh || width != cachedMeshWidth || height != cachedMeshHeight)
        if (trackedFrame != null) {
            if (cachedChanged) {
                if (width != cachedMeshWidth || height != cachedMeshHeight) {
                    cachedMeshRig = null
                    displayPose.reset()
                }
                val mesh = trackedFrame.face
                cachedMesh = trackedFrame
                cachedMeshWidth = width
                cachedMeshHeight = height
                val candidates = VtoHeadPose.candidates(mesh, width, height, state.scale, state.heightOffset)
                val heldAxes = cachedMeshRig?.let { VtoHeadPose.Axes(it.xAxis, it.yAxis, it.zAxis) }
                cachedMeshRig = candidates?.let {
                    it.fitted ?: heldAxes?.let { axes ->
                        VtoHeadPose.withAxes(mesh, width, height, state.scale, state.heightOffset, axes)
                    } ?: it.fallback
                }
                val box = model.boundingBox
                modelAnchor = GlbFaceTransform.frontAnchor(box.center, box.halfExtent, calibration)
                cachedMeshMatrix = GlbFaceTransform.matrix(mesh, width, height, box.center, box.halfExtent,
                    state.scale, state.heightOffset, calibration, cachedMeshRig)
                val matrix = cachedMeshMatrix
                hasMeshInverse = matrix != null && GlMatrix.invertM(inverseMeshMatrix, 0, matrix, 0)
                occluder?.requestMesh(mesh, width, height, cachedMeshRig, inverseMeshMatrix.takeIf { hasMeshInverse })
            }
            val meshMatrix = cachedMeshMatrix
            rawPoseMatrix = if (frameShift != null && meshMatrix != null &&
                VtoShiftedPose.apply(meshMatrix, frameShift, trackedFrame.toPreview, height.toFloat(), eyePoints, shiftedMatrix)) {
                // Moved, tilted and resized by how both eyes moved since the mesh's frame.
                shiftedMatrix
            } else meshMatrix
            poseFace = state.metrics
        } else {
            if (cachedMesh != null) {
                cachedMesh = null
                cachedMeshRig = null
                displayPose.reset()
                hasMeshInverse = false
                occluder?.clearPreparedMesh()
                GlMatrix.setIdentityM(maskTransform, 0)
                occluder?.setTransform(maskTransform)
                poseFace = null
            }
            val poseMetrics = state.metrics
            val geometryChanged=poseMetrics !== poseFace || width!=poseWidth || height!=poseHeight ||
                state.scale!=poseScale || state.heightOffset!=poseOffset
            val resolvedRig = if (geometryChanged || diagnosticFace !== lastMeasurement) {
                rigResolver.resolve(poseMetrics, width, height, state.scale, state.heightOffset, trackingNow, diagnosticFace)
            } else poseRig
            lastMeasurement=diagnosticFace
            val changed=geometryChanged || resolvedRig != poseRig
            if(changed) {
                pendingFrame=true
                poseRig=resolvedRig
                if (width != poseWidth || height != poseHeight) displayPose.reset()
                rawPoseMatrix=GlbFaceTransform.matrix(poseMetrics,width,height,
                    model.boundingBox.center,model.boundingBox.halfExtent,state.scale,state.heightOffset,calibration,resolvedRig)
                if (modelAnchor == null && rawPoseMatrix != null) {
                    val box = model.boundingBox
                    modelAnchor = GlbFaceTransform.frontAnchor(box.center, box.halfExtent, calibration)
                }
                occluder?.update(poseMetrics,width,height,rawPoseMatrix!=null,resolvedRig)
                poseFace=poseMetrics;poseWidth=width;poseHeight=height
                poseScale=state.scale;poseOffset=state.heightOffset
            }
        }
        val baseMatrix = rawPoseMatrix
        val anchorForCorrection = modelAnchor
        val rawMatrix = if (trackedFrame != null && baseMatrix != null && anchorForCorrection != null) {
            val at = com.example.facefit.ui.tryon.tracking.benchAnchor(baseMatrix, anchorForCorrection)
            // Steps under ~8% of the view are tracking disagreement to blend; larger ones are real.
            meshCorrection.apply(trackedFrame.referenceId, at.x, at.y, trackingNow, width * .08f, correction)
            // Fill in display frames between ~29 Hz positions and hide part of their age.
            if (sampleNanos != null) motionLead.apply(sampleNanos, at.x + correction[0], at.y + correction[1], trackingNow, lead)
            else { motionLead.reset(); lead[0] = 0f; lead[1] = 0f }
            baseMatrix.copyInto(correctedMatrix)
            correctedMatrix[12] += correction[0] + lead[0]
            correctedMatrix[13] += correction[1] + lead[1]
            correctedMatrix
        } else {
            meshCorrection.reset()
            motionLead.reset()
            baseMatrix
        }
        fun applyStabilizedPose() {
            val shown = if (rawMatrix == null) {
                displayPose.reset()
                null
            } else displayPose.sample(rawMatrix, requireNotNull(modelAnchor), trackingNow,
                // Eye span on screen sets how much motion counts as "still" for smoothing.
                ((if (trackedFrame != null) cachedMeshRig else poseRig)?.size ?: 0f))
            if (trackedFrame != null) occluder?.presentPreparedMesh(shown)
            if (shown !== poseMatrix && (shown == null || poseMatrix?.contentEquals(shown) != true)) {
                poseMatrix = shown
                pendingFrame = true
                shown?.let { engine.transformManager.setTransform(engine.transformManager.getInstance(model.root), it) }
            }
        }
        applyStabilizedPose()
        if (com.example.facefit.BuildConfig.DEBUG && android.util.Log.isLoggable("FaceFitVTOPose", android.util.Log.VERBOSE)) {
            // One line per display frame: shown bridge point, target bridge point, size and tilt.
            val shown = poseMatrix
            val anchor = modelAnchor
            if (shown != null && anchor != null && rawMatrix != null) {
                val s = com.example.facefit.ui.tryon.tracking.benchAnchor(shown, anchor)
                val t = com.example.facefit.ui.tryon.tracking.benchAnchor(rawMatrix, anchor)
                val zLength = kotlin.math.sqrt(shown[8] * shown[8] + shown[9] * shown[9] + shown[10] * shown[10])
                android.util.Log.v("FaceFitVTOPose", "t=${trackingNow / 1_000_000} mesh=${trackedFrame?.referenceId ?: 0} " +
                    "eyes=${frameShift != null} sx=${s.x} sy=${s.y} tx=${t.x} ty=${t.y} " +
                    "scale=${kotlin.math.hypot(shown[0], shown[1])} roll=${kotlin.math.atan2(shown[1], shown[0])} " +
                    "yaw=${kotlin.math.asin((shown[8] / zLength).coerceIn(-1f, 1f))} pitch=${kotlin.math.asin((shown[9] / zLength).coerceIn(-1f, 1f))}")
                // Once per mesh: where the glasses' centre lands vs the nose-bridge points of that same mesh.
                val mesh = trackedFrame
                val meshMatrix = cachedMeshMatrix
                if (mesh != null && meshMatrix != null && mesh !== loggedPlacement && mesh.face.landmarks.size >= 468) {
                    loggedPlacement = mesh
                    val m = com.example.facefit.ui.tryon.tracking.benchAnchor(meshMatrix, anchor)
                    fun world(i: Int) = mesh.face.landmarks[i].let { it.x * width to height - it.y * height }
                    val (b168x, b168y) = world(168); val (b6x, b6y) = world(6)
                    val (ax, ay) = world(33); val (bx, by) = world(263)
                    val zl = kotlin.math.sqrt(meshMatrix[8] * meshMatrix[8] + meshMatrix[9] * meshMatrix[9] + meshMatrix[10] * meshMatrix[10])
                    android.util.Log.v("FaceFitVTOPose", "placement mesh=${mesh.referenceId} ax=${m.x} ay=${m.y} " +
                        "b168x=$b168x b168y=$b168y b6x=$b6x b6y=$b6y eyeSpan=${kotlin.math.hypot(bx - ax, by - ay)} " +
                        "myaw=${kotlin.math.asin((meshMatrix[8] / zl).coerceIn(-1f, 1f))} mpitch=${kotlin.math.asin((meshMatrix[9] / zl).coerceIn(-1f, 1f))}")
                }
            }
        }
        if (!pendingFrame && !forceSubmit) return false
        val show = poseMatrix != null
        if (show != visible) {
            if (show) scene.addEntities(model.entities) else scene.removeEntities(model.entities)
            visible = show
        }
        // Clear transparent pixels even after tracking is lost, so the last pose cannot linger.
        val target = swapChain ?: return false
        val marked=gpuTiming.submitMarker(diagnosticFace)
        val diagnosticPoseDone = android.os.SystemClock.uptimeMillis()
        val submitted: Boolean
        try {
            submitted=renderer.beginFrame(target,time)
            if (submitted) {
                renderer.render(view)
                renderer.endFrame()
                com.example.facefit.ui.tryon.tracking.VtoBench.submitted(poseMatrix, (if (trackedFrame != null) cachedMeshRig else poseRig)?.size ?: 0f, trackedFrame?.sampledAtNanos, System.nanoTime(), modelAnchor ?: Vector3(0f, 0f, 0f))
                pendingFrame=false
                gpuTiming.submitted(diagnosticFace)
            }
        } finally { gpuTiming.endMarker(marked) }
        VtoDiagnostics.render(diagnosticFace,diagnosticStart,diagnosticPoseDone,
            android.os.SystemClock.uptimeMillis(), submitted, show, trackedFrame?.timing?.meshIntervalNanos ?: 0L)
        return submitted
    }

    fun close() {
        gpuTiming.close()
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

/** How long the previous tracked mesh may stay on screen while a new mesh waits for its first match. */
private const val MESH_HOLD_NANOS = 350_000_000L

/** Which mesh and eye correction to draw on a display frame. Pure, so it can be unit-tested. */
internal object VtoMeshChoice {
    enum class Pick { LATEST_WITH_NEW_SHIFT, LATEST_WITH_LAST_SHIFT, SHOWN_WITH_LAST_SHIFT, LATEST_UNTRACKED }

    fun pick(latestIsShown: Boolean, latestHasMatch: Boolean, latestTracked: Boolean, hasShown: Boolean,
        waitedNanos: Long): Pick = when {
        latestHasMatch -> Pick.LATEST_WITH_NEW_SHIFT
        latestIsShown -> Pick.LATEST_WITH_LAST_SHIFT
        hasShown && latestTracked && waitedNanos <= MESH_HOLD_NANOS -> Pick.SHOWN_WITH_LAST_SHIFT
        else -> Pick.LATEST_UNTRACKED
    }
}
