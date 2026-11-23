# Nearby Shops: registered Firebase retailers

## Current app connection

Mobile is already connected to `facefit-ai-capstone`, and the Nearby Shops route
uses live `publicShops` listeners (not demo shops). Each directory document uses
the same ID as its private source `retailers/{retailerId}`. Shop cards already open
the shop details page. App location is optional and is not written to Firebase.

The missing piece in the current checkout was the server-side publisher. This
update restores it. It publishes only approved, named retailers, copying
`retailName -> name`, `retailAddress -> address`, and
`shopLocation.lat/lng -> latitude/longitude`. Valid legacy coordinate formats and
genuine aggregate ratings are supported. No permit, owner, private application
contact or email fields are copied. Invalid coordinates do not remove a named
shop, but its distance remains unavailable. Revoked/deleted retailers are removed
from the public directory on the next sync event. Event handlers re-read current
data in a transaction so an old event cannot restore an unapproved shop.

## Activate on the correct Firebase project

This project uses Blaze. A Firebase CLI account with access to
**facefit-ai-capstone** must authorize deployment. The checked-in web/admin CLI
default is a DIFFERENT project; always pass `--project facefit-ai-capstone`.

1. Authenticate Firebase CLI using `firebase login`. Do not paste service-account
   keys, tokens or authorization codes into source files or chat.
2. In `web/admin/functions`, install the existing locked dependencies with
   `npm ci`. No changes to dependency versions are required for this publisher.
3. From `web/admin`, deploy ONLY this function:
   `firebase deploy --config firebase.nearby-shops.json --only functions:syncPublicShop --project facefit-ai-capstone --force`.
   Do not run a general deployment: the local complete rules file is older than
   the live rules supplied by the user, and hosting points at a different site.
   The separate config selects the supported Node.js 22 runtime for this deployment
   and includes no hosting/rules targets. The old default config and package engine
   remain unchanged; do not use them to deploy this function on retired Node.js 20.
4. Keep the publicShops read rule already published in the Firebase Console.
   `mobile/firebase/nearby-shops.directory.rules` is a reference fragment if it is
   missing. Merge just that block; never replace the full live rules with it.

## Publish retailers that already exist

Deploying a trigger does not generate events for existing unchanged documents.
Run the administrator backfill once using trusted Application Default Credentials
with Firestore access in facefit-ai-capstone. Firebase CLI login and Application
Default Credentials are separate; a successful CLI login alone does not guarantee
the local Admin SDK script is authenticated. Keep credentials outside this repo.

From `web/admin/functions`, first preview without writes:

```
node backfill-public-shops.js --project facefit-ai-capstone
```

Then publish the reviewed eligible records:

```
node backfill-public-shops.js --project facefit-ai-capstone --apply
```

The script processes retailers in pages, preserves source records and uses the
same current-source transaction as the live trigger. It rejects a missing or
different project argument before loading credentials. Approved retailer IDs are
preserved. It never changes retailer applications or their approval status.

## Check in Firebase and mobile

- In Firestore Data, verify `publicShops/{same-retailer-document-id}` exists and
  contains name, address, approved status and valid coordinates when available.
- Sign in to mobile and open Nearby Shops; use All shops before checking the 3 km
  radius. Pending/rejected retailers should not appear. Empty results are not
  proof of successful publication; verify the records in Firebase too.
- Update an approved shop's location/name and verify the public row updates. Test
  approval revocation/removal and customer access with an ordinary signed-in user.
- Shop descriptions/contact fields stay blank unless separately published as
  explicitly public data. No sample introduction or fake rating is generated.

## Frames: confirm the source before changing it

The mobile shop-details page currently queries top-level
`frames` with `retailId == shopId` and `status == approved`. The user's live rules
also include `retailers/{retailerId}/frames`. Check actual frame documents to
confirm the source and ownership field before changing that reader or permissions.
Do not expose private retailer documents to work around directory access.
`nearby-shops.frames.rules` is an older proposal for top-level frames only; it
must not replace the nested retailer frame rules or unrelated live rules.

## Files changed in this update

| File | Why |
| --- | --- |
| web/admin/functions/public-shop-projection.js | Allowlisted retailer-to-shop mapping using the actual portal coordinates. |
| web/admin/functions/public-shop-sync.js | Transactional publishing shared by live events and the initial backfill. |
| web/admin/functions/public-shops.js | Firestore trigger for retailer creation, edits, approval and removal. |
| web/admin/firebase.nearby-shops.json | Function-only Node.js 22 deployment config; avoids changing/deploying the old hosting and rules config. |
| web/admin/functions/index.js | Export the sync trigger without changing existing admin functions. |
| web/admin/functions/backfill-public-shops.js | Explicit-project, dry-run-first initial publishing for existing retailers. |
| web/admin/functions/public-shop-projection.test.js | Mapping, approval, privacy, coordinate and rating checks. |
| web/admin/functions/public-shop-sync.test.js | Current-source publishing, removal and wrong-project checks. |
| mobile/firebase/nearby-shops.directory.rules | Minimal directory read permission reference; not deployed automatically. |
| mobile/NEARBY_SHOPS_SETUP.md | Accurate activation, verification and changed-file instructions. |

The Kotlin screens, VTO, authentication, web UI and kiosk are unchanged. Local
tests cannot prove live Firebase data/permissions; record deployment/backfill
results separately before claiming the feature is active.


## Verified activation — 13 November 2026

- Firebase sign-in and project access succeeded for facefit-ai-capstone.
- The default Firestore database location was verified as asia-southeast1.
- The publisher was deployed as `syncPublicShop` in asia-southeast1, Node.js 22,
  and its Cloud Functions API state was verified as ACTIVE. Its event filter is
  `retailers/{retailerId}` on the default database, with retries enabled. The
  current-source transaction is idempotent; CLI requires --force when enabling
  the function retry policy. Runtime database access was checked.
- Initial publishing used the authorized CLI session through an isolated local
  operator helper. No service-account key was created or committed. There were
  6 registered retailers and 5 eligible approved, named retailers. All 5 public
  rows were written and read back successfully, with only the allowlisted fields.
- Only one of those shops currently has valid coordinates. All shops should be
  browsable under All shops; missing coordinates must not be invented.
- The already-published publicShops rule was confirmed. No live Firestore rules,
  retailer source records, email function or website hosting were changed.
- All 9 local publisher tests passed, and the SDK function export, trigger path
  and deployment configuration were validated. Customer phone testing and a
  future retailer-change event have not been observed in this session.
- No new APK is required for this backend-only activation. Reopen Nearby Shops
  and select All shops in the already updated mobile app.

### Confirmed frame data — next integration step

Live inspection found both product formats:

- `frames/FRM001`: `retailerId`, `status: approved`, `frameName`, `frameType`,
  `frameColor`, `frameMaterial`, `imageURL`, `price`.
- `retailers/{retailerId}/frames/{frameId}`: `retailerId`, `status: live`, `name`,
  `shape`, `color`, `material`, `description`, `productImageUrl`, plus VTO metadata.

The current Kotlin reader instead uses top-level `retailId` and `frameImage`, so
it cannot show these actual products correctly yet. Live customer frame reads
are also restricted by the current frame rules. The remaining frame integration
must align both schema and permissions while preserving owner/admin write rules.
Do not claim the frame details listing is fixed by publishing shops alone.


## Nearby Shops card/logo update — 13 November 2026

The list follows the supplied reference with white rounded cards, pale blue
background, soft shadows and an orange location pin over a blue base. The left
tile loads the shop's logo with Fit scaling; missing, loading or failed images
show a teal location icon. Whole-card navigation and the separate directions
button are preserved. Ratings/distances are real or explicitly unavailable.

The existing shop publisher now copies only the business image URL into logoUrl,
using logoUrl, logo, profileImageUrl or profileImage (first valid HTTPS URL).
Live inspection found two approved retailers using profileImageUrl. The existing
publicShops directory is reused; no new collection or source retailer writes are
needed. Redeploy syncPublicShop and refresh existing public rows so old shops
receive their image URLs. This does not change frame data or frame permissions.

Files changed in this card update:

| File | Change and reason |
| --- | --- |
| mobile/app/src/main/java/com/example/facefit/ui/shops/NearbyShopsScreen.kt | Reference-style cards, logo tile and location fallback, accessible illustrated directions pin, compact map. |
| mobile/app/src/main/java/com/example/facefit/data/shops/ShopDirectoryModels.kt | Read logoUrl separately from a shop cover photo. |
| mobile/app/src/test/java/com/example/facefit/ShopDirectoryModelsTest.kt | Check logo/cover separation and invalid URL fallback. |
| web/admin/functions/public-shop-projection.js | Copy the registered shop's valid logo URL into the existing public directory. |
| web/admin/functions/public-shop-projection.test.js | Check actual image-field aliases, precedence and unsafe/missing image values. |
| mobile/NEARBY_SHOPS_SETUP.md | Document this change and exact edited files. |


Card/logo update verification:

- Redeployed the existing syncPublicShop function successfully and refreshed the
  existing 5 public shop rows. Only 2 have a logoUrl; 3 retain the location fallback.
  Source retailer documents and Firestore rules were not changed. Both logo image
  URLs returned HTTP 200 with image/png content types.
- All 11 backend tests passed. The Android debug APK was generated successfully.
- Full Gradle unit-test compilation is blocked by a pre-existing measurement type
  mismatch in FaceShapeClassifierTest.kt:75, outside this card change. No classifier
  file was edited. Both shop test files were separately compiled from actual source
  with the project's cached Kotlin 2.2.10 compiler and JUnit: all 10 tests passed.
- For this verification only, build outputs used D: because C: was nearly full.
  No project build settings were edited. APK:
  D:/AndroidStudioProjects/FaceFit-verification/FaceFitAI-build/app/outputs/apk/debug/app-debug.apk
- Phone rendering, image fallback timing and physical small-screen appearance
  remain to be checked by the user. No device installation was performed.


## Shop details: logos and real frames — 13 November 2026

ShopLogo.kt is now shared by Nearby Shops and Shop Details, including the same
location-icon fallback. Detail headers use a larger logo without cropping it.
The frame reader merges three live, shop-scoped queries: nested retailer frames
with status live/approved, top-level approved frames owned by retailerId, and
top-level approved legacy frames owned by retailId. Query listeners detach on
screen exit. Results deduplicate the same top-level record returned by both owner
queries while preserving distinct retailer products with the same document ID.

The model reads actual name/frameName, productImageUrl/imageURL/frameImage,
frameType, frameMaterial and frameColor fields, and rejects wrong/conflicting
ownership, drafts, pending and hidden records. A missing/broken image gets the
existing image placeholder without hiding a valid product. No new collection,
fake products, source frame writes or VTO model changes are needed.

The customer-read rules patch is prepared from the CURRENT live rules, preserving
all admin/owner writes and unrelated collections. It adds read-only access to
published products of approved public shops. Signed-out users and private shop
applications remain inaccessible. The older complete web admin rules must not be
deployed over this live policy. Test the full candidate policy using the local
demo Firestore emulator before publishing the narrowly scoped read additions.

Files changed in this step:

| File | Change and reason |
| --- | --- |
| mobile/app/src/main/java/com/example/facefit/ui/shops/ShopLogo.kt | Shared logo tile with the location fallback for list and details. |
| mobile/app/src/main/java/com/example/facefit/ui/shops/NearbyShopsScreen.kt | Reuse the shared logo tile. |
| mobile/app/src/main/java/com/example/facefit/ui/shops/ShopDetailsScreen.kt | Display the selected shop logo and its published frames. |
| mobile/app/src/main/java/com/example/facefit/data/shops/NearbyShops.kt | Read actual nested and legacy frame sources for the selected shop. |
| mobile/app/src/main/java/com/example/facefit/data/shops/ShopDirectoryModels.kt | Map real product names/images/attributes and filter ownership/status. |
| mobile/app/src/test/java/com/example/facefit/ShopDirectoryModelsTest.kt | Test real schemas, ownership, visibility, deduplication and image fallback. |
| mobile/firebase/nearby-shops.frames.rules | Record the precise customer frame-read additions; preserve existing writes. |
| mobile/firebase/nearby-shops.rules.test.cjs | Local customer/owner/admin frame-access tests. |
| mobile/NEARBY_SHOPS_SETUP.md | Implementation, permissions and validation record. |


Shop-details verification and activation:

- All 14 focused Kotlin shop/model tests passed from actual sources using Kotlin
  2.2.10 and JUnit. The unrelated full-suite classifier-test mismatch is unchanged.
- All 8 access-policy tests passed in the local demo Firestore emulator: exact
  customer queries, visibility restrictions, signed-out denial, private retailer
  denial, customer write denial, preserved owner edits and admin access.
- The tested read-only additions were published to facefit-ai-capstone. The live
  ruleset version was checked before publishing so concurrent rule changes could
  not be overwritten. The new active release was read back successfully.
- Original live rules and release metadata are backed up under
  D:/AndroidStudioProjects/FaceFit-verification/frame-rule-tests/.
- The exact three production queries were run for each real approved shop to
  verify indexes and ownership: ClearView Optical, Test and Glasses each returned
  1 published frame. Chirped Glasses and Disposable Audit Optical returned 0.
- No frame documents, retailer applications or collections were created/edited.
  Only customer read permission for published products was added in Firebase.
- Android assembleDebug succeeded. Updated APK:
  D:/AndroidStudioProjects/FaceFit-verification/FaceFitAI-build/app/outputs/apk/debug/app-debug.apk
- Phone rendering and customer-device interaction have not been tested here.
  Install/run the updated mobile build, open each shop, confirm its logo, product
  names/images and frame count, then return and open a different shop.

Rules test prerequisites: @firebase/rules-unit-testing 4.0.1 and firebase 11.10.0,
plus the official local Firestore emulator. Set FACEFIT_RULES_FILE to a complete
candidate policy, NODE_PATH to those test dependencies, and run
node --test mobile/firebase/nearby-shops.rules.test.cjs with the emulator on
127.0.0.1:8184. The test project is demo-facefit-nearby; its fixtures never write
to the live project. The .rules reference fragment is not a complete policy.


## Full product page — 13 November 2026

Pressing a shop frame now navigates to shopProduct/{shopId}/{frameKey}, instead
of opening a dialog. It uses stable encoded IDs and re-subscribes to the exact
product document, preserving correct behavior after navigation/process recreation.
Only known retailer/catalog document keys are accepted. Product removal or revoked
visibility clears the page and shows an unavailable/retry state.

The FaceFit design includes a large uncropped product photo, an airy teal header,
product name and attributes, description, available size/measurement rows, seller
logo/address link, a functional native text-share action and shop directions.
Only real prices/ISO currency are formatted; a price without a currency is shown
without an invented symbol and explicitly marked for shop confirmation. Missing
prices and measurements are stated. Current product measurement fields are empty.
No fake variants, reviews, 360 views, checkout or try-on capability are implied.

Files changed:

| File | Change and reason |
| --- | --- |
| mobile/app/src/main/java/com/example/facefit/ui/shops/ShopProductDetailsScreen.kt | New responsive full product page in FaceFit styling. |
| mobile/app/src/main/java/com/example/facefit/ui/shops/ShopDetailsScreen.kt | Replace the product dialog with a frame-click callback; share the existing network image renderer. |
| mobile/app/src/main/java/com/example/facefit/navigation/FaceFitNavHost.kt | Add stable product-page navigation and return-to-shop behavior. |
| mobile/app/src/main/java/com/example/facefit/data/shops/NearbyShops.kt | Observe one shop-owned product document for the product page. |
| mobile/app/src/main/java/com/example/facefit/data/shops/ShopDirectoryModels.kt | Parse real prices, currency, measurements and validated document keys. |
| mobile/app/src/test/java/com/example/facefit/ShopDirectoryModelsTest.kt | Test product key validation, missing/invalid price and measurement fields. |
| mobile/firebase/nearby-shops.rules.test.cjs | Check direct product-detail reads using the existing published access policy. |
| mobile/NEARBY_SHOPS_SETUP.md | Change and verification record. |


Full product page verification:

- Android assembleDebug completed successfully, including the new page and route.
- All 17 focused model/route tests passed. All 9 local emulator access tests passed,
  including direct document reads used by the new page. The existing unrelated
  full-suite classifier test compilation issue was not changed or masked.
- No live Firebase rules or records changed in this UI step. It uses the previously
  verified customer product-read policy and existing actual product records.
- APK: D:/AndroidStudioProjects/FaceFit-verification/FaceFitAI-build/app/outputs/apk/debug/app-debug.apk
- On-phone appearance, system share chooser, directions and back-navigation still
  need device verification. No device was installed or operated in this step.
