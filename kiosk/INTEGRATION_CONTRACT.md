# Kiosk analyzer and frame integration

The customer UI consumes data contracts. Providers are selected in MainActivity:

- `KioskAnalyzer = MobileKioskAnalyzer(...)`: mobile quality-gated multi-frame scan and the facefit_18b ONNX model. See MOBILE_SCAN_INTEGRATION.md.
- `FrameCatalog = MockFrameCatalog()`: local records for demo-shop with explicit visual-preview score fixtures (not analyzer measurements).
- `MockShopAuthRepository`: existing local authentication, still not real retailer authentication.

## Kenn's analyzer

Implement `KioskAnalyzer.analyze(capturedImagePath)` and return `KioskAnalysisOutcome`.
The processing screen calls this off the main thread, handles no-face/multiple-face/failure,
retains the five-second minimum, and stores a successful `FaceScanResult` in the session.
Replace the analyzer construction in MainActivity. The screens do not need Kenn-specific code.

`FaceScanResult` contains:

- `faceShape: FaceShape` (required); `FaceShape.fromLabel(rawLabel)` normalizes external labels.
- `confidencePercent: Int?` (0Ã¢â‚¬â€œ100, null when unavailable; convert a 0Ã¢â‚¬â€œ1 probability in the adapter only if Kenn documents that unit).
- `notes: List<String>` (optional user-facing descriptions).
- `metrics: FacialFeatureMetrics?` (optional normalized ratios, not millimeters).
- `isEstimate: Boolean` (defaults to true; only change when the analyzer's semantics justify it).
- `analysisId: String?` (optional source result ID).
- `modelId`, `modelScores`, `modelDecisionPolicy`, `modelAgreement`: optional model provenance. Model scores are displayed separately from confidence.

Kenn's actual payload/schema has not been supplied. This is an internal contract, not a claim
that his output already has these field names. Agree on labels, units, confidence meaning,
errors, and image input format before writing the mapping adapter. Unknown labels map to
Undetermined, never Oval. Diamond has a neutral illustration until a matching PNG is supplied.

## Retailer / Firebase

Implement `FrameCatalog.recommendationsFor(profile, shopId)` using authenticated shop-scoped data.
The Results screen calls it on a worker thread and supports loading, empty, error/retry,
variable-length inventory, optional scores, selection, and unavailable-item filtering.
Return recommendations in provider order. Scores must be 0Ã¢â‚¬â€œ100 or null; null displays Not scored.
Replace `MockFrameCatalog()` in MainActivity when ready. A Firebase adapter can bridge its async
client using a bounded worker-side wait or evolve this interface to suspend; never wait on UI.
Enforce shop access in backend rules; a client-provided shopId alone is not authorization.

FrameItem currently carries ID, name, shape, color, material, and availability. Product-image
URLs/loading, dimensions, VTO assets, and real matching still need retailer contracts and adapters.
The current illustrations are generic fallback drawings; VTO now reuses the mobile implementation; see VTO_INTEGRATION.md for assets and limitations.

## Checks

Run `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
ResultContractTest covers external non-Oval labels, missing optional fields, optional scores and preview score fixtures,
shop separation for local records, failure mapping, and clearing stale state on rescan.