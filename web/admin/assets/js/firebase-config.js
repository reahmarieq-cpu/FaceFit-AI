// ============================================================
// FaceFit AI — Centralized Firebase Configuration
// ------------------------------------------------------------
// Every other JS file imports auth/db FROM THIS FILE.
// This is the ONLY place initializeApp() is called.
// Loaded via CDN (Firebase JS SDK 12.18.0) — no npm, no bundler.
// ============================================================

import { initializeApp } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-app.js";
import { getAuth, connectAuthEmulator } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-auth.js";
import { getFirestore, connectFirestoreEmulator } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";
import { getFunctions, connectFunctionsEmulator } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-functions.js";

// Your project's actual web config (facefit-ai-ca57f)
const firebaseConfig = {
  apiKey: "AIzaSyC2WK1jO_-HYsXN9hjd7Noo7mMgy7Be5f0",
  authDomain: "facefit-ai-ca57f.firebaseapp.com",
  projectId: "facefit-ai-ca57f",
  storageBucket: "facefit-ai-ca57f.firebasestorage.app",
  messagingSenderId: "150859960988",
  appId: "1:150859960988:web:5360f0e27b51ce72dbd359",
  measurementId: "G-JLJH94YL54"
};

// initializeApp() is called ONCE, here. Every other module (auth.js,
// admin-guard.js, retailers.js, retailer-detail.js, etc.) imports the
// resulting auth/db/functions instances from this file — none of them
// ever call initializeApp() themselves.
const app = initializeApp(firebaseConfig);

export const auth = getAuth(app);
export const db = getFirestore(app);
export const functions = getFunctions(app);

// Note: Analytics (getAnalytics) is intentionally NOT initialized here.
// It isn't needed for admin authentication/dashboard functionality,
// and skipping it keeps this file focused on what Phase 1 actually
// uses. It can be added later if the project needs it.

// ------------------------------------------------------------
// OPT-IN local emulator support (Cloud Functions validation
// without requiring the Blaze plan).
//
// Off by default — every existing page keeps talking to the real
// facefit-ai-ca57f project exactly as it does today. Emulators only
// activate when a page is loaded with ?useEmulators=true in the URL,
// so this cannot accidentally change behavior for normal testing.
//
// Requires: `firebase emulators:start` running locally (see README).
// Auth/Firestore data in the emulator is separate from production —
// the admin account and retailers/100001 must be re-created inside
// the emulator UI (http://localhost:4000) before testing.
// ------------------------------------------------------------
const useEmulators = new URLSearchParams(window.location.search).get("useEmulators") === "true";

if (useEmulators) {
  console.warn("[FF] Connecting to LOCAL EMULATORS, not production Firebase.");
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  connectFirestoreEmulator(db, "127.0.0.1", 8080);
  connectFunctionsEmulator(functions, "127.0.0.1", 5001);
}
