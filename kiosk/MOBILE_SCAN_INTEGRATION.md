# Mobile scan and analysis integration

Ported the active mobile `facefit_18b` model, quality checks, geometry features, stable-frame collection, and highest-average-score decision policy.
The kiosk Start Scan button begins collection; it requires the same pose/lighting/readiness rules and at least 15 fresh frames over two seconds.
The still photo remains a kiosk review step. Continue runs the model on the collected batch; the five-second analyzing presentation is retained.
No geometric face-shape fallback is used in the active analysis path. Model scores are labelled as model scores, not calibrated confidence.
VTO sources and the mobile project were not changed. Scan resources live separately under facefit/mobilescan.

## Files changed and why

- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceAnalyzer.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceAnalyzer.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FrameImageScope.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FrameImageScope.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FacialGeometry.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FacialGeometry.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/ScanStabilityFeatures.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/ScanStabilityFeatures.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceScanPose.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceScanPose.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceScanLighting.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceScanLighting.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceScanReadiness.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceScanReadiness.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceScanSamples.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceScanSamples.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceShapeDecision.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceShapeDecision.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceShapeOnnxModel.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/FaceShapeOnnxModel.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/ScanStatistics.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/ScanStatistics.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/camera/TrackingBackend.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/camera/TrackingBackend.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/mobilescan/camera/TryOnImageBuffer.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/mobilescan/camera/TryOnImageBuffer.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/assets/experiment_18b/classes.txt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/experiment_18b/classes.txt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/assets/experiment_18b/facefit_18b.onnx](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/experiment_18b/facefit_18b.onnx) — Ported mobile scan logic or parity fixtures.
- [app/src/main/assets/experiment_18b/features.txt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/experiment_18b/features.txt) — Ported mobile scan logic or parity fixtures.
- [app/src/main/assets/experiment_18b/metadata.json](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/assets/experiment_18b/metadata.json) — Ported mobile scan logic or parity fixtures.
- [app/build.gradle](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/build.gradle) — Connect live scan collection, model inference, result metadata, or kiosk guidance.
- [app/src/main/java/com/facefit/kiosk/data/session/KioskSessionStore.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/data/session/KioskSessionStore.kt) — Connect live scan collection, model inference, result metadata, or kiosk guidance.
- [app/src/main/java/com/facefit/kiosk/model/FaceScanResult.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/model/FaceScanResult.kt) — Connect live scan collection, model inference, result metadata, or kiosk guidance.
- [app/src/main/java/com/facefit/kiosk/facefit/analysis/MobileKioskAnalyzer.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/analysis/MobileKioskAnalyzer.kt) — Connect live scan collection, model inference, result metadata, or kiosk guidance.
- [app/src/main/java/com/facefit/kiosk/MainActivity.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/MainActivity.kt) — Connect live scan collection, model inference, result metadata, or kiosk guidance.
- [app/src/main/java/com/facefit/kiosk/facefit/camera/CameraCaptureGateway.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/camera/CameraCaptureGateway.kt) — Connect live scan collection, model inference, result metadata, or kiosk guidance.
- [app/src/main/java/com/facefit/kiosk/facefit/camera/CameraXCameraCaptureGateway.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/camera/CameraXCameraCaptureGateway.kt) — Connect live scan collection, model inference, result metadata, or kiosk guidance.
- [app/src/main/java/com/facefit/kiosk/ui/scan/KioskFacePositioningView.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/ui/scan/KioskFacePositioningView.kt) — Connect live scan collection, model inference, result metadata, or kiosk guidance.
- [app/src/main/java/com/facefit/kiosk/ui/results/KioskResultsView.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/ui/results/KioskResultsView.kt) — Connect live scan collection, model inference, result metadata, or kiosk guidance.
- [app/src/main/res/values/mobile_scan_strings.xml](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/res/values/mobile_scan_strings.xml) — Connect live scan collection, model inference, result metadata, or kiosk guidance.
- [app/src/test/java/com/facefit/kiosk/mobilescan/FaceScanReadinessTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/mobilescan/FaceScanReadinessTest.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/test/java/com/facefit/kiosk/mobilescan/FaceScanPoseTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/mobilescan/FaceScanPoseTest.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/test/java/com/facefit/kiosk/mobilescan/FaceShapeDecisionTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/mobilescan/FaceShapeDecisionTest.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/test/java/com/facefit/kiosk/mobilescan/ScanStabilityFeaturesTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/mobilescan/ScanStabilityFeaturesTest.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/test/java/com/facefit/kiosk/mobilescan/FacialGeometryTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/java/com/facefit/kiosk/mobilescan/FacialGeometryTest.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/androidTest/java/com/facefit/kiosk/mobilescan/FaceShapeOnnxModelTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/androidTest/java/com/facefit/kiosk/mobilescan/FaceShapeOnnxModelTest.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/androidTest/java/com/facefit/kiosk/mobilescan/FaceScanLightingTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/androidTest/java/com/facefit/kiosk/mobilescan/FaceScanLightingTest.kt) — Ported mobile scan logic or parity fixtures.
- [app/src/androidTest/assets/exp18b_geometry_reference.json](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/androidTest/assets/exp18b_geometry_reference.json) — Ported mobile scan logic or parity fixtures.
- [app/src/androidTest/assets/exp18b_shape_reference.json](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/androidTest/assets/exp18b_shape_reference.json) — Ported mobile scan logic or parity fixtures.
- [app/src/main/java/com/facefit/kiosk/facefit/camera/MockCameraCaptureGateway.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/main/java/com/facefit/kiosk/facefit/camera/MockCameraCaptureGateway.kt) — Match the expanded scan camera contract.

- [app/src/test/resources/exp18b_geometry_reference.csv](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/test/resources/exp18b_geometry_reference.csv) — Derived from the unchanged mobile Android geometry-reference JSON to supply the missing JVM fixture.
- [MobileKioskAnalyzerTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/androidTest/java/com/facefit/kiosk/mobilescan/MobileKioskAnalyzerTest.kt) — Check native-model-to-kiosk mapping, scores and rejection of a photo without a scan batch.

- [KioskStableScanTest.kt](D:/AndroidStudioProjects/FaceFit-AI/kiosk/app/src/androidTest/java/com/facefit/kiosk/mobilescan/KioskStableScanTest.kt) — Test quality gating, interrupted-batch reset, still capture, and Continue through the actual kiosk scan view using a controlled camera.

- [INTEGRATION_CONTRACT.md](D:/AndroidStudioProjects/FaceFit-AI/kiosk/INTEGRATION_CONTRACT.md) — Document the active mobile-model adapter and result provenance fields.
- [MOBILE_SCAN_INTEGRATION.md](D:/AndroidStudioProjects/FaceFit-AI/kiosk/MOBILE_SCAN_INTEGRATION.md) — File-by-file integration notes and verification limits.

## Validation

- Debug app and instrumentation APKs build successfully; Android lint passed.
- All 94 JVM unit tests passed, including the ported mobile quality, stability, feature and decision tests.
- All 7 Android checks passed on a temporary API 36 emulator: native ONNX output parity, MediaPipe scan graph initialization, lighting behavior, kiosk result mapping, missing-batch rejection, and controlled kiosk scan flow.
- The ONNX model and its classes/features/metadata are byte-identical to the mobile assets.
- The physical tablet was disconnected. This build has not been installed there, and real camera capture/alignment and hardware use-case support still need an on-tablet check.
- The temporary headless emulator was used only for verification and is shut down afterward.

## Using the kiosk

Sync Gradle after opening the project because ONNX Runtime was added. On the camera page, follow the pose/lighting guidance, tap Start Scan when ready, hold steady for two seconds, review the photo, then Continue. Analysis uses the collected mobile-model batch and retains the kiosk's five-second minimum presentation.
