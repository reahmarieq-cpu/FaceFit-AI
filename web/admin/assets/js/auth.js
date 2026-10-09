// ============================================================
// FaceFit AI — Authentication Module
// ------------------------------------------------------------
// Reusable auth functions shared by login.html and the
// admin-guard. No page-specific DOM code lives here.
// ============================================================

import { auth, db } from "./firebase-config.js";
import {
  signInWithEmailAndPassword,
  signOut,
  onAuthStateChanged
} from "https://www.gstatic.com/firebasejs/12.18.0/firebase-auth.js";
import {
  doc,
  getDoc
} from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";

/**
 * Checks whether a given Firebase Auth UID is registered as an
 * Administrator. This is the ONLY source of truth for the admin
 * role — it is never inferred from the email address on the client.
 *
 * Trust model: the "admins" collection is readable only by the
 * signed-in user for their OWN document, and is not writable from
 * the client at all (see firestore.rules). An admin document is
 * created manually via the Firebase Console, never by the web app.
 */
export async function verifyAdminRole(uid) {
  console.log("[FF-DEBUG] Step 4 — Looking up Firestore doc: admins/" + uid);
  const adminRef = doc(db, "admins", uid);

  let adminSnap;
  try {
    adminSnap = await getDoc(adminRef);
  } catch (error) {
    console.error("[FF-DEBUG] Step 4 FAILED — Firestore getDoc() threw an error.");
    console.error("[FF-DEBUG]   error.code:", error.code);
    console.error("[FF-DEBUG]   error.message:", error.message);
    console.error("[FF-DEBUG]   Common codes: permission-denied (rules deny this read), " +
      "unauthenticated (no auth token attached), not-found / failed-precondition (Firestore misconfigured).");
    throw error;
  }

  console.log("[FF-DEBUG] Step 5 — Document exists?", adminSnap.exists());

  if (!adminSnap.exists()) {
    console.warn("[FF-DEBUG] Step 5 — No document at admins/" + uid +
      ". This means the Firestore document ID does not exactly match this UID " +
      "(check for typos, extra spaces, or a truncated UID).");
    return false;
  }

  const data = adminSnap.data();
  console.log("[FF-DEBUG] Step 6 — Document data:", data);
  console.log("[FF-DEBUG] Step 6 — role field value:", JSON.stringify(data.role), "| typeof:", typeof data.role);

  const isAdmin = data.role === "admin";
  console.log("[FF-DEBUG] Step 7 — role === \"admin\" ?", isAdmin);

  return isAdmin;
}

/**
 * Signs in with email/password, then confirms the account is an
 * authorized Administrator. If the account is NOT an administrator,
 * the session is immediately signed out and an error is thrown so
 * the login page can display a clear message.
 */
export async function loginAdmin(email, password) {
  console.log("[FF-DEBUG] Step 1 — Firebase Auth instance ready. authDomain:", auth.config?.authDomain);
  console.log("[FF-DEBUG] Step 2 — Calling signInWithEmailAndPassword() for:", email);

  let credential;
  try {
    credential = await signInWithEmailAndPassword(auth, email, password);
  } catch (error) {
    console.error("[FF-DEBUG] Step 2 FAILED — signInWithEmailAndPassword() threw an error.");
    console.error("[FF-DEBUG]   error.code:", error.code);
    console.error("[FF-DEBUG]   error.message:", error.message);
    console.error("[FF-DEBUG]   Common codes: auth/invalid-credential, auth/user-not-found, " +
      "auth/wrong-password, auth/unauthorized-domain, auth/configuration-not-found.");
    throw error;
  }

  console.log("[FF-DEBUG] Step 2 OK — signed in.");
  console.log("[FF-DEBUG] Step 3 — Authenticated UID:", credential.user.uid, "| length:", credential.user.uid.length);
  console.log("[FF-DEBUG] Step 3 — Authenticated email:", credential.user.email);

  const isAdmin = await verifyAdminRole(credential.user.uid);

  if (!isAdmin) {
    console.warn("[FF-DEBUG] Step 7 — NOT authorized as admin. Signing back out.");
    await signOut(auth);
    throw new Error("This account is not authorized as an Administrator.");
  }

  console.log("[FF-DEBUG] Step 8 — Authorized. Proceeding to dashboard redirect.");
  return credential.user;
}

/** Signs the current user out and returns them to the login page. */
export async function logoutAdmin() {
  await signOut(auth);
  window.location.href = new URLSearchParams(window.location.search).get("useEmulators") === "true" ? "login.html?useEmulators=true" : "login.html";
}

/** Thin wrapper around onAuthStateChanged for pages that need it directly. */
export function watchAuthState(callback) {
  return onAuthStateChanged(auth, callback);
}
