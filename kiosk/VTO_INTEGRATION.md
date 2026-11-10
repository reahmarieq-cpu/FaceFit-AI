# Mobile VTO integration

Mobile sources are copied into the isolated `com.facefit.kiosk.vto` package. The mobile project is unchanged.
Camera tracking, smoothing, occlusion, 2D/layered overlays, and Filament GLB rendering retain the mobile implementation.
Compose is hosted only for the VTO screen; the kiosk continues using its existing Views elsewhere.
FrameItem.tryOn carries the selected asset configuration. The temporary catalog now uses the mobile's eight frame records;
Milo and Leo have no assets and remain unavailable. Scores are preview fixtures, not measured recommendations.
Retailer integration must supply packaged/local asset paths and calibration metadata matching VtoFrame; remote URLs are not yet supported.
Back returns to details, then to results. Composition disposal releases VTO resources when leaving the screen.

## Files changed and why

- [app/src/main/java/com/facefit/kiosk/vto/ui/tryon/VirtualTryOnScreen.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/tryon/VirtualTryOnScreen.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/tryon/rendering/FilamentGlassesOverlay.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/tryon/rendering/FilamentGlassesOverlay.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/tryon/rendering/Glasses3DOverlay.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/tryon/rendering/Glasses3DOverlay.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/tryon/rendering/Glasses3DRenderer.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/tryon/rendering/Glasses3DRenderer.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/tryon/rendering/LayeredImageTryOnOverlay.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/tryon/rendering/LayeredImageTryOnOverlay.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/tryon/rendering/TryOnOverlay.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/tryon/rendering/TryOnOverlay.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/tryon/rendering/VtoFaceOccluder.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/tryon/rendering/VtoFaceOccluder.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/tryon/tracking/VtoDiagnostics.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/tryon/tracking/VtoDiagnostics.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/tryon/tracking/VtoTrackingFeed.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/tryon/tracking/VtoTrackingFeed.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/geometry/Glasses3D.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/geometry/Glasses3D.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/geometry/GlbFaceTransform.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/geometry/GlbFaceTransform.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/geometry/LayeredImageGeometry.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/geometry/LayeredImageGeometry.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/geometry/TryOnGeometry.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/geometry/TryOnGeometry.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/geometry/VtoAutomaticFit.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/geometry/VtoAutomaticFit.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/geometry/VtoHeadOcclusionGeometry.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/geometry/VtoHeadOcclusionGeometry.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/tracking/VtoHeadPose.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/tracking/VtoHeadPose.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/tracking/VtoLandmarkStabilizer.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/tracking/VtoLandmarkStabilizer.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/tracking/VtoPoseSmoother.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/tryon/tracking/VtoPoseSmoother.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/geometry/FaceMeshTopology.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/geometry/FaceMeshTopology.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/analysis/FaceAnalyzer.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/analysis/FaceAnalyzer.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/camera/TrackingBackend.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/camera/TrackingBackend.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/facefit/camera/TryOnImageBuffer.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/camera/TryOnImageBuffer.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/camera/TryOnCameraPreview.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/camera/TryOnCameraPreview.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/camera/VtoCameraFrame.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/camera/VtoCameraFrame.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/camera/VtoPreviewCapture.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/camera/VtoPreviewCapture.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/components/ResultIconButton.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/components/ResultIconButton.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/theme/ContainerStyle.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/theme/ContainerStyle.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/catalog/FrameCatalog.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/catalog/FrameCatalog.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/java/com/facefit/kiosk/vto/ui/theme/Colors.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/ui/theme/Colors.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/browline/black_browline_angled.png](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/browline/black_browline_angled.png) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/browline/black_browline_front_depth.png](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/browline/black_browline_front_depth.png) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/browline/black_browline_front_layer.png](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/browline/black_browline_front_layer.png) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/browline/black_browline_temple.png](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/browline/black_browline_temple.png) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/cat-eye/model.glb](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/cat-eye/model.glb) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/cat-eye/thumbail.png](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/cat-eye/thumbail.png) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/pax/pax.png](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/pax/pax.png) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/rectangle/arm_image_left](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/rectangle/arm_image_left) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/rectangle/arm_image_right](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/rectangle/arm_image_right) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/rectangle/front](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/rectangle/front) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/rectangle/model.png](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/rectangle/model.png) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/Round/model.glb](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/Round/model.glb) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/Round/thumbnail.png](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/Round/thumbnail.png) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/square_tortoise/SquareTortoise.glb](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/square_tortoise/SquareTortoise.glb) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/frames/square_tortoise/tumbnail.png](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/frames/square_tortoise/tumbnail.png) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/main/assets/vto/face_triangles.bin](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/vto/face_triangles.bin) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/build.gradle](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/build.gradle) — Connect selected frames, VTO navigation, availability, build dependencies or validation.
- [app/src/main/java/com/facefit/kiosk/model/FrameItem.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/model/FrameItem.kt) — Connect selected frames, VTO navigation, availability, build dependencies or validation.
- [app/src/main/java/com/facefit/kiosk/data/frames/MockFrameCatalog.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/data/frames/MockFrameCatalog.kt) — Connect selected frames, VTO navigation, availability, build dependencies or validation.
- [app/src/main/java/com/facefit/kiosk/navigation/KioskDestination.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/navigation/KioskDestination.kt) — Connect selected frames, VTO navigation, availability, build dependencies or validation.
- [app/src/main/java/com/facefit/kiosk/MainActivity.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/MainActivity.kt) — Connect selected frames, VTO navigation, availability, build dependencies or validation.
- [app/src/main/java/com/facefit/kiosk/ui/frames/KioskFrameDetailsView.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/ui/frames/KioskFrameDetailsView.kt) — Connect selected frames, VTO navigation, availability, build dependencies or validation.
- [app/src/main/res/layout/screen_frame_details.xml](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/res/layout/screen_frame_details.xml) — Connect selected frames, VTO navigation, availability, build dependencies or validation.
- [app/src/main/res/layout-w1000dp/screen_frame_details.xml](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/res/layout-w1000dp/screen_frame_details.xml) — Connect selected frames, VTO navigation, availability, build dependencies or validation.
- [app/src/main/res/values/frame_details_strings.xml](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/res/values/frame_details_strings.xml) — Connect selected frames, VTO navigation, availability, build dependencies or validation.
- [app/src/test/java/com/facefit/kiosk/model/ResultContractTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/model/ResultContractTest.kt) — Connect selected frames, VTO navigation, availability, build dependencies or validation.
- [app/src/test/java/com/facefit/kiosk/vto/Glasses3DTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/vto/Glasses3DTest.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/test/java/com/facefit/kiosk/vto/GlbFaceTransformTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/vto/GlbFaceTransformTest.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/test/java/com/facefit/kiosk/vto/LayeredImageGeometryTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/vto/LayeredImageGeometryTest.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/test/java/com/facefit/kiosk/vto/TryOnGeometryTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/vto/TryOnGeometryTest.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/test/java/com/facefit/kiosk/vto/VtoAutomaticFitTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/vto/VtoAutomaticFitTest.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/test/java/com/facefit/kiosk/vto/VtoHeadPoseTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/vto/VtoHeadPoseTest.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/test/java/com/facefit/kiosk/vto/VtoLandmarkStabilizerTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/vto/VtoLandmarkStabilizerTest.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [app/src/test/java/com/facefit/kiosk/vto/VtoPoseSmootherTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/vto/VtoPoseSmootherTest.kt) — Ported mobile VTO implementation/assets for kiosk reuse.
- [FaceShapeClassifier.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/vto/facefit/analysis/FaceShapeClassifier.kt) — mobile analyzer dependency; does not replace the kiosk result analyzer.
- [KioskVtoIntegrationTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/androidTest/java/com/facefit/kiosk/KioskVtoIntegrationTest.kt) — verify asset packaging, selected-frame navigation, Back and missing-asset availability.
- [INTEGRATION_CONTRACT.md](D:/AndroidStudioProjects/FaceFit-AI/kiosk/INTEGRATION_CONTRACT.md) — update the VTO integration status.

Build with JDK 21 for the existing Gradle toolchain. This port uses Compose 1.6-compatible APIs and the mobile Filament version; LocalLifecycleOwner imports were adapted to the kiosk Compose version.

## Validation

- Debug APK built successfully using JDK 21.
- 57 unit tests passed, including the ported tracking and geometry suites.
- 2 kiosk integration tests passed on SM-T736B / Android 14: packaged asset checks and selected-frame navigation/Back/missing-asset availability.
- Android lint completed with 0 errors; existing and ported-source warnings remain.
- The tablet must be awake for lifecycle-dependent navigation tests. An initial run while it was asleep failed the Back assertion; the awake rerun passed.
- These checks do not validate live visual face alignment or physical frame fit. Check those with a person in front of the kiosk camera, including head turns and 3D frames.

Test flow: scan → results → choose Pax (2D), Nova or Square Tortoise (3D) → Start Virtual Try-On → Back to details. Milo and Leo stay disabled until assets are supplied.
