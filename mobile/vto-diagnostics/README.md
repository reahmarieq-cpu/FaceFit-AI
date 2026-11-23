# VTO timing and verification

The normal `FaceFitVTODiag` session measures analysis callback / preview readback to CPU submission. It does not claim camera-to-display latency.

## Second timing session (debug build only)

Run `record-vto-timing.ps1 -Serial <adb-serial> -AdbPath <path-to-adb.exe>` from PowerShell. Reopen VTO when prompted. This enables `FaceFitVTOTiming`, records a separate 15-second Perfetto session, saves trace/log files in `captures`, then disables the extra logging. No image or landmark coordinates are logged. Android 12+ and device FrameTimeline support are needed for compositor timing. No recording is automatically run by building the app.

- `sensorAgeMs`: measured at the analyzer callback **only** when Camera2 declares `SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME`, so its timestamps can be compared to `elapsedRealtimeNanos`. Otherwise explicitly unavailable. This is frame timestamp age, not photon-to-result time; exposure/rolling shutter are not separately measured.
- `gpuCompletionObservedNs`: first nonblocking Filament fence observation after submission. The observation is an **upper bound**, delayed by display-frame polling. It is not an exact hardware completion timestamp. Fence sampling is bounded to about one result per second and four outstanding fences. Diagnostic overhead can affect a run.
- `displayPresentation`: explicitly `requiresPerfettoFrameTimeline` in Logcat. Filament's TextureView does not expose an exact hardware display-present timestamp to app Java. Choreographer expected-presentation time and a TextureView update/commit callback must not be labeled actual presentation. Use `presentation.sql` in [Perfetto](https://ui.perfetto.dev/) to inspect actual app/compositor frame timelines beside `FaceFitVTO.submit` markers. Correct buffer/frame-token correlation is required before claiming exact end-to-end latency; dropped frames and unsupported devices may leave it unavailable.

References: [CameraX frame dropping](https://developer.android.com/media/camera/camerax/analyze), [PreviewView sensor transform](https://developer.android.com/reference/androidx/camera/view/PreviewView#getSensorToViewTransform()), [FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline).

## Coordinates and ownership

`GlbFaceTransform.kt` is not modified. CameraX Preview and RGBA ImageAnalysis share one ViewPort. CameraX rotates analysis pixels upright. Inference uses the shared viewport crop and mirrors those pixels once, matching the previous inference-on-mirrored-preview convention. Before mapping to the view, crop offsets and this inference-only mirror are undone to recover upright full-buffer coordinates (including width scaling for Z). Then map into PreviewView with `sensorToView * inverse(sensorToBuffer)`; the preview transform supplies the display mirror exactly once. No second display flip is applied. The resulting normalized X/Y and width-scaled Z use the existing preview convention. If the camera rejects bounded <=640-edge / <=307200-pixel buffers, or supplies invalid transforms, use the previously aligned preview-readback fallback. Actual analysis dimensions are logged.

Only the worker accesses the native face tracker and reusable RGBA/scaled inputs. The tracker borrows camera/preview pixels and owns its Bitmap/MPImage; closing it cannot recycle a fallback's input. GPU creation, blank warm-up and inference failures that throw an exception use a single CPU replacement, not simultaneous probes. Native SIGSEGV is not catchable in Kotlin and still requires physical-device validation.

The display follower runs on each Choreographer frame, with exponential adaptive error-based following and no prediction. It holds short losses for 250 ms, fades the TextureView over 100 ms, and snaps after convergence to stop redundant mesh uploads. Head-pose solver failures need three distinct measurements before changing mode, then axes are blended and orthogonalized. The same followed landmarks and resolved rig drive glasses and depth-only occlusion. CPU inference speed and performance on other phones still need measurement.
