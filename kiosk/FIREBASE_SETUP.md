# FaceFit-AI Kiosk Firebase setup

## Install the integration

1. Extract the integration archive and copy the **contents** of its `Firebase-Kiosk` directory into `C:\Users\Admin\FaceFit-AI\kiosk`, preserving paths. Review existing local changes before replacing files. Each Kotlin and Gradle file is a complete replacement for the corresponding file in the inspected kiosk source.
2. In Firebase Console, register an Android app with package `com.facefit.kiosk`. Place its downloaded config at `C:\Users\Admin\FaceFit-AI\kiosk\app\google-services.json`. If you set up Storage after registering the app, download the config again and make sure it specifies the intended bucket. Keep Admin SDK credentials out of the Android project.
3. Enable **Authentication → Sign-in method → Email/Password**. Use the existing retailer Auth account and copy its UID. In Firestore, locate the existing retailer document under retailers. Its userId must match the Auth UID, subscriptionPlan must be kioskplan, and subscriptionStatus must be active. Copy the retailer document ID separately. One retailer Auth identity can be shared by the kiosk devices for its shop.
4. Create the default Cloud Firestore database and a default Cloud Storage bucket. Cloud Storage requires a Blaze project. Products and shop records are provisioned by a trusted administrator through the console or Admin SDK.
5. From the kiosk directory, deploy the included rules to a test Firebase project first. For the shared FaceFit Firebase project, merge the kiosk functions and match blocks into the rules already used by that project and check for overlapping grants. Deploy the merged files from the shared project rules directory. The standalone deploy command below is for an isolated test project. Storage will prompt to enable the permission to read the default Firestore database from rules.

```powershell
firebase login
firebase deploy --project YOUR_FIREBASE_PROJECT_ID --only firestore:rules,storage
```

6. Seed a shop and frame using the examples below, then start the app and sign in with the retailer email and password. Rows are loaded when a scan reaches Results.

## Android paths

| File under the kiosk root | Purpose |
| --- | --- |
| `build.gradle`, `app/build.gradle` | Google Services 4.4.2, Firebase BoM 32.8.1 and requested KTX modules, and coroutines Play Services 1.7.3 |
| `app/src/main/AndroidManifest.xml` | Internet permission |
| `app/src/main/java/com/facefit/kiosk/MainActivity.kt` | Production repository selection |
| `app/src/main/java/com/facefit/kiosk/data/auth/FirebaseAuthRepository.kt` | Email/password authentication and server verification of the enabled shop |
| `app/src/main/java/com/facefit/kiosk/data/auth/ShopAuthRepository.kt`, `MockShopAuthRepository.kt` in the same directory | Suspend login contract and compatible mock |
| `app/src/main/java/com/facefit/kiosk/data/frames/FirebaseFrameCatalog.kt` | Worker-side Firestore fetch, mapping and ranking |
| `app/src/main/java/com/facefit/kiosk/model/FrameItem.kt` | Optional authenticated Storage preview path |
| `app/src/main/java/com/facefit/kiosk/ui/auth/ShopLoginView.kt` | Async sign-in and actionable errors |
| `app/src/main/java/com/facefit/kiosk/ui/results/FrameArtworkView.kt` | Bounded preview download and image decoding |
| `app/src/main/java/com/facefit/kiosk/ui/results/KioskResultsView.kt`, `app/src/main/java/com/facefit/kiosk/ui/frames/KioskFrameDetailsView.kt` | Bind previews on Results and Frame Details |
| `app/src/main/res/values/strings.xml` | Login feedback |
| `firebase.json`, `firestore.rules`, `storage.rules` | CLI configuration and deployable rules |

BoM 32.8.1 is a pinned baseline for the requested standalone KTX artifacts and existing Android toolchain. Firebase discontinued standalone KTX updates and removed them from BoM 34 onward. A future SDK upgrade should switch to the main `firebase-auth`, `firebase-firestore`, and `firebase-storage` modules and check the newer SDK toolchain requirements.

## Schema

**`shops/{shopId}`** where `shopId` is the Auth UID.

```json
{
  "retailerId": "EXISTING_RETAILER_DOC_ID",
  "displayName": "FaceFit Optical",
  "enabled": true
}
```

**`shops/{shopId}/frames/{frameId}`** where the document ID is the FrameItem ID. In this example the ID is `arbor-001`. Upload the image into the default bucket at the exact value of `previewStoragePath`.

```json
{
  "name": "Arbor 001",
  "shape": "Rectangle",
  "color": "Matte Black",
  "material": "Acetate",
  "isAvailable": true,
  "displayOrder": 10,
  "previewStoragePath": "shops/REPLACE_WITH_AUTH_UID/frame-previews/arbor-001.webp",
  "recommendations": {
    "round": {
      "compatibilityPercent": 88,
      "faceShapeScore": 90,
      "explanation": ["Rectangular rims add contrast to a round face shape."]
    }
  }
}
```

The five core name/shape/color/material/availability fields are required. Optional `displayOrder` is a Firestore integer. Recommendation keys are `oval`, `round`, `square`, `heart`, `oblong`, and `diamond`. Entries can contain `compatibilityPercent`, `faceShapeScore`, `proportionFitScore`, `styleColorScore` as Firestore integers from 0 to 100, and `explanation` as an array of strings. The app sorts by descending compatibility, then ascending display order and frame ID. An undetermined face shape or missing score leaves the frame unscored. This is a face-shape ranking using retailer-supplied scores. Preview files should be PNG, JPEG, or WebP, up to 2 MiB with a target long edge of 1024 pixels. Remote VTO models and calibration need a separate asset contract.

**`shops/{shopId}/scans/{scanId}`** is reserved for optional telemetry. An example trusted-server record contains `createdAt` (server timestamp), `faceShape` (e.g. `round`), `confidencePercent` (integer or null), and optionally `modelId` (string). Set retention if telemetry is enabled. The delivered kiosk keeps scans and metrics local. The deployed rules grant kiosk clients no scan-document access or scan-object access. Any future scan collection or media upload should be implemented as an explicit opt-in with a retention and deletion policy.

## Verify

With JDK 17, Android SDK 34 and the Firebase Android config installed, run from the kiosk root.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

- Sign in as Shop A. Confirm a successful scan produces only Shop A inventory, unavailable rows are omitted, and frames without scores still render.
- Remove all available frames. Confirm Results displays the empty state. Disconnect during Results, then reconnect and tap Retry. Confirm that a load failure prompts a retry and successful retry repopulates the list.
- Try incorrect credentials, an Auth user with no shop document, a disabled shop, and a shop with a missing or mismatched retailerId. Confirm each ineligible account is denied. Sign in with an active kioskplan retailer, then change subscriptionStatus to inactive in the test project and confirm subsequent catalog and Storage reads fail. Repeat with an active non-kiosk plan.
- From a test client signed in as Shop A, attempt to get Shop B's shop document, list Shop B's frames, and download Shop B's preview object. Repeat signed out. All must fail. Confirm catalog writes, Storage uploads, and scan reads/writes are denied from the app.
- Supply a missing or corrupt preview object and confirm the artwork fallback. Check both Results and Frame Details.
- Instrumentation tests that seed only the in-memory shop now need a Firebase test session and seeded backend inventory to exercise the production catalog. Demo-only behavior can be exercised with explicit mock providers.
