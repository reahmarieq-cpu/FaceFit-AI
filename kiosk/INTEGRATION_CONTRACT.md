# Kiosk analyzer and frame integration

The customer UI consumes data contracts. Providers are selected in MainActivity:

- `KioskAnalyzer = MobileKioskAnalyzer(...)`: quality-gated multi-frame capture and the mobile EfficientNetB0 LiteRT model. See MOBILE_SCAN_INTEGRATION.md.
- `FrameCatalog = FirebaseFrameCatalog()`: reads and ranks the signed-in shop's Firestore inventory.
- `ShopAuthRepository = FirebaseAuthRepository()`: signs in with Firebase Auth and verifies the shop is enabled.

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

Browse Frames and scan recommendations resolve the enabled shop's retailerId and read
live documents at `retailers/{retailerId}/frames`. The previous shop catalog is used when
there are no live retailer listings. Existing retailer compatibility scores take precedence. The mobile assessor dataset supplies scores for remaining face-shape and frame-shape combinations. Retailer entries can supply a scoped `productImagePath`
and a `vtoAssetPath` under `frame-models/{retailerId}/{frameId}/` for Storage-backed try-on.
Use `FrameCatalog.recommendationsFor(profile, shopId)` for ranked scan results.
The Results screen calls it on a worker thread and supports loading, empty, error/retry,
variable-length inventory, optional scores, selection, and unavailable-item filtering.
Return recommendations in provider order. Scores must be 0Ã¢â‚¬â€œ100 or null. Results omits frames without scores.
`FirebaseFrameCatalog` bridges the async client with a bounded worker-side wait.
Enforce shop access in backend rules; a client-provided shopId alone is not authorization.

`FrameItem` carries an optional shop-scoped Storage preview path. Missing or failed previews use
existing fallback artwork. Recommendations use retailer-configured face-shape scores. See
FIREBASE_SETUP.md for provisioning, schema, rules and checks, and VTO_INTEGRATION.md for VTO.

## Checks

Run `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
ResultContractTest covers external non-Oval labels, missing optional fields, optional scores and preview score fixtures,
shop separation for local records, failure mapping, and clearing stale state on rescan.