# FaceFit AI — Firebase Storage local integration

## Important
The upload API runs on **your own computer**, not as a hosted service. Do not expose port 3000 to the internet.

## Setup
1. Keep your existing `backend/serviceAccountKey.json` and `backend/.env` PRIVATE. If replacing the project folder, copy them from your old backend into this package's `backend/` folder. Never put them in `web/`.
2. Use the `facefit-ai-capstone.firebasestorage.app` bucket with the project's `web/admin/storage.rules`. Keep `FIREBASE_PROJECT_ID=facefit-ai-capstone`.
3. Run `npm install`, then `npm start` inside `backend`. Open `http://localhost:3000/health`; `storageProvider` should be `firebase`.
4. In a separate terminal, run `cd web` then `python -m http.server 8000`.
5. Open `http://localhost:8000/retailer/register.html` and register with a JPG, PNG or PDF permit **under 5 MB**. If an earlier attempt created an Auth account but not a Firestore retailer document, retry with the same email and password.

## Folders in Firebase Storage
- `business-permits`: PRIVATE, max 5 MB, PDF/JPG/PNG
- `shop-logos`: shareable download link, max 2 MB, JPG/PNG/WebP
- `frame-images`: shareable download link, max 2 MB, JPG/PNG/WebP
- `vto-models`: PRIVATE, max 10 MB, GLB

## Important caveats
- The backend requires Firebase Admin service-account credentials to check Firestore retailer/admin roles. Never commit them or `.env`.
- Permits and GLBs use private `firebase://` references. The backend checks ownership or admin access before issuing a five-minute download link. Any separate kiosk build must support the same private-file endpoint.
- Admin approval Cloud Functions / Firestore rules must already be configured separately.
- `node --test tests/frame-delete.test.mjs` checks deletion without using cloud data. The live disposable test is `node tests/cloud-delete.mjs --disposable-approved` and requires an approved test run.
