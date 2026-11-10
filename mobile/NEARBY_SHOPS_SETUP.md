# Nearby Shops: Firebase directory and shop details

The mobile app now uses live Firestore listeners, not demo shops. Tapping a shop
opens its information and approved frames; the directions icon opens Maps.
Location is optional: without it, all published shops remain browsable. With it,
the default radius is 3 km (straight-line), with an All shops option. Missing
coordinates are listed separately. No location is written to Firebase.

## Firebase project and permissions

The mobile app targets **facefit-ai-capstone**. The checked-in web admin Firebase
CLI default points to a different project, so always specify the intended project
explicitly when using Firebase CLI. Nothing was deployed by this update.

1. Sign in to the mobile app with a Firebase account.
2. Check Firestore in facefit-ai-capstone: approved retailers must be published as
   `publicShops/{retailerId}`. The existing trusted `syncPublicShop` function does
   this, and removes shops whose approval is revoked. Private `retailers` are never
   queried by the mobile directory.
3. If the publisher has not been activated, review/deploy only that function using
   `firebase deploy --only functions:syncPublicShop --project facefit-ai-capstone`
   from web/admin. Existing retailers also need the existing administrator backfill
   script, with trusted credentials explicitly targeting facefit-ai-capstone.
   This update does not execute that script or write live records.
4. Review live Firestore rules. The checked-in rules currently allow only admins
   to read frames. Merge `firebase/nearby-shops.frames.rules` into the live rules,
   replacing only the frames match block. It is a fragment, not a complete file.
   Keep the existing isAdmin() helper and all unrelated rules. It grants signed-in
   users read access only to approved frames belonging to a published approved
   shop. Do not enable general public reads or client writes.
5. Keep the existing publicShops read rule: signed-in users may read approved
   records; clients cannot write public directory records. Publish reviewed rules
   through the Firebase Console. Do not deploy the old web rules file unchanged,
   because it does not include the new frame read permission.

The app shows a retry/error message if reads are denied; it does not substitute
dummy shops or products. Live permissions and data must be verified in Firebase.

## Data read by mobile

`publicShops/{retailerId}`:

| Field | Use |
| --- | --- |
| status | Must be approved |
| name, address | Shop heading and address |
| latitude, longitude | Optional numeric map coordinates |
| rating | Optional real rating from 0 to 5 |
| description, phone, website, openingHours, imageUrl | Optional explicitly public shop introduction/contact/cover fields |

The existing publisher currently emits name, address, status, coordinates and real
rating only. About shows an honest placeholder until public descriptions are
published. Extend that trusted publisher separately if retailer-managed public
introductions, business contact details or hours are required; it may overwrite
manual edits on its next sync. Private application contactNumber, permits, email
and owner details are not exposed by this feature.

`frames/{frameId}`:

| Field | Use |
| --- | --- |
| retailId | Must exactly equal the selected publicShops document ID |
| status | Must be approved |
| brand, model, type, shape, material, color | Actual frame details |
| frameImage | HTTPS image URL, including Firebase Storage download URLs |
| description | Optional description |

Images load with Coil and show a neutral fallback when missing/unavailable. No
sample frames, prices, fit scores, stock promises or VTO models are invented.
Frame rows open an image/details dialog. The shop's frame list uses the two
equality filters above; no local sample catalog is involved. If Firestore requests
an index, create the specific index linked in its error in the Firebase Console.

## Verification

Build the mobile debug app, run JVM unit tests and compile instrumentation tests.
On a phone, sign in and check: actual approved shops; location allowed/denied;
All shops; a shop with/without coordinates; only that shop's approved frames;
actual image URLs; directions; frame dialog; empty listings; permission/network
errors and retry. Change/hide a frame or revoke a shop approval and verify the
open page updates. Firestore listeners detach when their screen leaves composition.
Phone tests and live Firebase permission tests were not performed in this update.

The existing bundled Leaflet map and its license are unchanged. OpenStreetMap
tiles require internet; attribution remains visible.

## Retailer portal location mapping

The confirmed retailer schema saves its map pin as `shopLocation: {lat, lng}`.
The public shop publisher now maps that pair to the mobile `latitude` and
`longitude` fields, before considering legacy coordinate formats. A malformed
current pin is omitted, rather than replaced with stale coordinates. Only
approved retailers with a nonempty retailName are published; private application
fields remain excluded. Redeploy syncPublicShop after this mapper update, then
publish existing retailer records with the administrator backfill. Updating local
code or publishing access rules alone does not create publicShops documents.

The actual live rules also include nested `retailers/{retailerId}/frames` in
addition to top-level `frames`. Confirm which collection contains the real
retailer products before changing the mobile frame query; this update changes
shop location mapping only.
