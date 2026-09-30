// ============================================================
// FaceFit AI — Login Page Script
// ------------------------------------------------------------
// DOM wiring only. All real auth logic lives in auth.js.
// ============================================================

import { loginAdmin } from "./auth.js";

const form = document.getElementById("login-form");
const emailInput = document.getElementById("email");
const passwordInput = document.getElementById("password");
const errorBox = document.getElementById("login-error");
const submitBtn = document.getElementById("login-btn");

function showError(message) {
  errorBox.textContent = message;
  errorBox.classList.remove("d-none");
}

function clearError() {
  errorBox.textContent = "";
  errorBox.classList.add("d-none");
}

function setLoading(isLoading) {
  submitBtn.disabled = isLoading;
  submitBtn.textContent = isLoading ? "Signing in…" : "Sign In";
}

// Maps raw Firebase error codes to messages an admin can act on.
function friendlyErrorMessage(error) {
  const code = error?.code || "";

  if (code === "auth/invalid-email") return "Please enter a valid email address.";
  if (code === "auth/user-not-found" || code === "auth/wrong-password" || code === "auth/invalid-credential") {
    return "Incorrect email or password.";
  }
  if (code === "auth/too-many-requests") return "Too many attempts. Please wait a moment and try again.";
  if (code === "auth/unauthorized-domain") {
    return "This page is being served from a domain Firebase Auth doesn't recognize. Use http://localhost:<port>, not 127.0.0.1 or a raw file path.";
  }
  if (code === "permission-denied" || code === "firestore/permission-denied") {
    return "Signed in, but Firestore denied the admin-role lookup. This usually means firestore.rules hasn't been deployed yet — run: firebase deploy --only firestore:rules";
  }

  // Our own thrown error for a non-admin account.
  if (error?.message?.includes("not authorized")) return error.message;

  return "Unable to sign in right now. Please try again. (Check the browser console for the exact error code.)";
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearError();

  const email = emailInput.value.trim();
  const password = passwordInput.value;

  if (!email || !password) {
    showError("Email and password are both required.");
    return;
  }

  setLoading(true);
  try {
    await loginAdmin(email, password);
    window.location.href = "dashboard.html";
  } catch (error) {
    console.error("Login failed:", error);
    showError(friendlyErrorMessage(error));
  } finally {
    setLoading(false);
  }
});

// If admin-guard.js bounced the visitor back here (e.g. a Firestore
// permission error, or a non-admin account), show the real reason
// instead of leaving them wondering why they're back at login.
const guardNotice = sessionStorage.getItem("ff_guard_notice");
if (guardNotice) {
  showError(guardNotice);
  sessionStorage.removeItem("ff_guard_notice");
}
