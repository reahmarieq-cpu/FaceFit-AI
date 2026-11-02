// ============================================================
// FaceFit AI — Administrator Route Guard
// ------------------------------------------------------------
// Import and call guardAdminPage() at the TOP of every protected
// admin page's script. It resolves ONLY when the visitor is a
// signed-in, verified Administrator. Otherwise it redirects to
// login.html — so typing a dashboard URL directly, without being
// signed in as an admin, never renders protected content.
// ============================================================

import { auth, db } from "./firebase-config.js";
import { onAuthStateChanged, signOut } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-auth.js";
import { doc, getDoc } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";

/**
 * Resolves with { user, adminData } once verified.
 * Redirects to login.html and never resolves if the visitor is not
 * an authenticated, role-verified administrator.
 */
export function guardAdminPage() {
  return new Promise((resolve) => {
    onAuthStateChanged(auth, async (user) => {
      console.log("[FF-DEBUG][guard] onAuthStateChanged fired. user:", user ? user.uid : null);

      // Not signed in at all.
      if (!user) {
        console.warn("[FF-DEBUG][guard] No signed-in user — redirecting to login.html");
        window.location.href = "login.html";
        return;
      }

      try {
        console.log("[FF-DEBUG][guard] Looking up Firestore doc: admins/" + user.uid);
        const adminSnap = await getDoc(doc(db, "admins", user.uid));
        console.log("[FF-DEBUG][guard] Document exists?", adminSnap.exists());

        if (adminSnap.exists()) {
          console.log("[FF-DEBUG][guard] Document data:", adminSnap.data());
        }

        // Signed in, but NOT a verified administrator.
        if (!adminSnap.exists() || adminSnap.data().role !== "admin") {
          console.warn("[FF-DEBUG][guard] Not authorized as admin — signing out and redirecting.");
          await signOut(auth);
          sessionStorage.setItem(
            "ff_guard_notice",
            "This account is not authorized as an Administrator."
          );
          window.location.href = "login.html";
          return;
        }

        console.log("[FF-DEBUG][guard] Authorized. Rendering protected page.");
        resolve({ user, adminData: adminSnap.data() });
      } catch (error) {
        console.error("[FF-DEBUG][guard] Firestore getDoc() threw an error.");
        console.error("[FF-DEBUG][guard]   error.code:", error.code);
        console.error("[FF-DEBUG][guard]   error.message:", error.message);
        const isPermissionError = error?.code === "permission-denied";
        sessionStorage.setItem(
          "ff_guard_notice",
          isPermissionError
            ? "Signed in, but Firestore denied the admin-role lookup. Run: firebase deploy --only firestore:rules"
            : "Something went wrong verifying your admin access. Please sign in again."
        );
        window.location.href = "login.html";
      }
    });
  });
}
