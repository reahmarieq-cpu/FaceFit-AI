# Files changed for the ordered VTO fix

All paths below are relative to the mobile project. `GlbFaceTransform.kt` is unchanged. No catalog, shops, authentication, Firebase or kiosk files are changed by this update.

- `app/src/main/java/com/example/facefit/facefit/tryon/tracking/VtoDisplayFollower.kt` — New display-rate exponential follower, adaptive tau, no prediction, convergence identity, 250 ms grace and 100 ms fade state.
- `app/src/main/java/com/example/facefit/facefit/tryon/tracking/VtoLandmarkStabilizer.kt` — Validation pass-through; removes the second temporal filter.
- `app/src/main/java/com/example/facefit/facefit/tryon/tracking/VtoHeadPose.kt` — Exposes fitted/fallback candidates and the original bridge/orientation calculation; keeps pure rig API available.
- `app/src/main/java/com/example/facefit/facefit/tryon/tracking/VtoRigResolver.kt` — New measurement-based hysteresis and rigid-axis blending between pose solvers.
- `app/src/main/java/com/example/facefit/ui/tryon/rendering/FilamentGlassesOverlay.kt` — Runs follower each Choreographer frame, fades TextureView, shares followed pose/rig, polls optional GPU timing, preserves raw diagnostic identity.
- `app/src/main/java/com/example/facefit/ui/camera/VtoRgbaBuffer.kt` — New native bulk RGBA copy with reusable row packing and borrowed input ownership.
- `app/src/main/java/com/example/facefit/ui/camera/VtoCameraFrame.kt` — Reusable bulk-copy entry point and crop/mirror restoration before the existing sensor-to-preview transform.
- `app/src/main/java/com/example/facefit/ui/camera/TryOnCameraPreview.kt` — Worker CameraX latest-only RGBA analysis, shared ViewPort, bounded resolution/fallback, mirrored crop parity, GPU-first eager warm-up/CPU fallback, reusable scaled input, lower confidences and lifecycle cleanup.
- `app/src/main/java/com/example/facefit/ui/tryon/tracking/VtoDiagnostics.kt` — Separate opt-in full-timing correlation, sensor age only for known compatible timebases; normal CPU timing preserved.
- `app/src/main/java/com/example/facefit/ui/tryon/rendering/VtoGpuTiming.kt` — New opt-in nonblocking GPU fence observations and trace markers; explicitly labels timing upper bounds.
- `vto-diagnostics/frametimeline.pbtxt` — 15-second Perfetto configuration for frame timelines, scheduling and graphics traces.
- `vto-diagnostics/record-vto-timing.ps1` — Explicit-device recording helper that captures logs/traces and restores the previous diagnostic log level.
- `vto-diagnostics/presentation.sql` — Queries actual FrameTimeline presentation data; requires buffer/frame-token correlation before reporting end-to-end latency.
- `vto-diagnostics/README.md` — Explains recording, timing limits, coordinate conventions, input ownership and fallback behavior.
- `vto-diagnostics/.gitignore` — Keeps locally recorded timing captures out of version control.
- `app/src/test/java/com/example/facefit/VtoDisplayFollowerTest.kt` — Focused unit tests for the new follower, validation or pose-solver policy.
- `app/src/test/java/com/example/facefit/VtoLandmarkStabilizerTest.kt` — Focused unit tests for the new follower, validation or pose-solver policy.
- `app/src/test/java/com/example/facefit/VtoRigResolverTest.kt` — Focused unit tests for the new follower, validation or pose-solver policy.
- `app/src/main/java/com/example/facefit/ui/tryon/rendering/VtoFaceOccluder.kt` — Refreshes geometry when the shared blended rig changes, even if landmarks are unchanged.
- `app/src/androidTest/java/com/example/facefit/VtoCameraFrameTest.kt` — Android regression tests for coordinate/pixel mapping or borrowed bitmap ownership.
- `app/src/androidTest/java/com/example/facefit/VtoTrackerOwnershipTest.kt` — Android regression tests for coordinate/pixel mapping or borrowed bitmap ownership.

- `vto-diagnostics/CHANGES.md` — This complete list of changed files and verification results.

Native GPU/camera behavior and actual phone latency are not verified by JVM or emulator-only tests. The user chose to test the built app themselves. Actual display presentation requires a supported system trace; no expected-vsync/CPU-submit value is labeled actual presentation.

## Verification for this update

- Debug app and Android instrumentation APKs: built successfully offline.
- Focused JVM tests against the compiled app classes: 29 passed (display following, loss/reacquisition, validation and solver hysteresis/blending).
- Coordinate/pixel and preview-capture instrumentation methods completed before the combined native render run aborted. The combined instrumentation suite did not finish successfully, so it is not reported as a passing suite.
- Filament rendering on the headless emulator was blocked by its legacy SwiftShader backend failing to compile Filament's built-in FXAA shader. No production rendering workaround was added for that emulator failure. The alternate emulator retry did not register with ADB and was stopped.
- Native MediaPipe GPU behavior, real-camera alignment, actual display presentation and phone latency remain unverified. No phone installation or camera test was performed, as requested.
- Full existing unit-suite compilation is separately blocked by an existing FaceShapeClassifierTest Measurements signature mismatch; the 29 focused tests do not imply that full suite passed.
- Whitespace checks passed. GlbFaceTransform.kt matches its pre-update SHA-256 backup.
